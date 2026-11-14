package com.readme.postingread;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.readme.common.config.AppProperties;
import com.readme.common.error.ApiException;

/**
 * 공고 페이지 한 장을 받는다. jobfit 의 src/jobfit/fetch/fetcher.py 에서 받기·인코딩 풀기를, robots.py 에서 robots.txt 확인을
 * 옮기고, 리드미가 링크를 받는 규칙(2026-11-15)과 SSRF 막기를 더했다.
 *
 * <ul>
 * <li>정직한 고유 이름(User-Agent)에 연락할 주소(프런트 주소)를 붙인다. 로그인, 쿠키, 프록시, 캡차·차단 우회는 쓰지 않는다.</li>
 * <li>받는 페이지마다 그 사이트의 robots.txt 를 따른다. robots.txt 는 사이트마다 한 시간 기억한다.</li>
 * <li>같은 호스트에는 1초에 하나만 보낸다({@link HostThrottle}).</li>
 * <li>주소를 넘어가라는 응답(redirect)은 직접 따라가며 세 번까지, 넘어갈 때마다 주소 검사와 robots.txt 를 다시 한다.</li>
 * <li>연결 5초, 응답 전체 10초, 본문 1MB 까지. text/html 만 받는다.</li>
 * <li>받은 본문은 기록하지 않는다. 실패하면 호스트와 이유만 남긴다.</li>
 * </ul>
 * 받지 못하면 422 FETCH_BLOCKED(사이트가 막음)나 FETCH_FAILED(그 밖)이고, 메시지는 본문 붙여넣기로 안내한다.
 */
@Component
class PageFetcher {

	static final String NAME = "ReadmeFetcher/1.0";

	/** 본문 한도. jobfit 의 실측 페이지 가운데 가장 큰 것이 470KB 다. 읽은 문서가 메모리를 많이 쓰므로 작게 둔다(PostingReader). */
	static final int MAX_BYTES = 1024 * 1024;

	private static final int MAX_REDIRECTS = 3;

	private static final Duration ROBOTS_TTL = Duration.ofHours(1);

	/** robots.txt 를 기억하는 사이트 수. 넘으면 모두 잊는다. */
	private static final int ROBOTS_CACHE_SIZE = 1000;

	private static final String PASTE = " 공고 본문을 복사해 붙여넣어 주세요";

	private static final Pattern META_CHARSET = Pattern.compile("<meta[^>]+charset=[\"']?\\s*([A-Za-z0-9_-]+)",
			Pattern.CASE_INSENSITIVE);

	private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

	private final String userAgent;

	private final PostingReadProperties settings;

	private final HttpClient client;

	private final HostThrottle throttle;

	/** 출처(스킴://호스트:포트) → robots.txt. shortcut: 서버 한 대의 메모리에만 있다, 서버를 여러 대 띄우면 공유 저장소로 옮긴다. */
	private final Map<String, CachedRobots> robots = new ConcurrentHashMap<>();

	@Autowired
	PageFetcher(AppProperties app, PostingReadProperties settings) {
		this(NAME + " (+" + app.frontendOrigin() + ")", settings);
	}

	PageFetcher(String userAgent, PostingReadProperties settings) {
		this.userAgent = userAgent;
		this.settings = settings;
		this.throttle = new HostThrottle(settings.hostInterval(), System::nanoTime);
		this.client = HttpClient.newBuilder()
			.connectTimeout(settings.connectTimeout())
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(HttpClient.Builder.NO_PROXY)
			.version(HttpClient.Version.HTTP_1_1)
			.build();
	}

	/** 받은 페이지. url 은 넘어간 뒤의 마지막 주소다. */
	record Page(URI url, String html) {
	}

	/**
	 * 공고 페이지 한 장.
	 *
	 * @param referer 보낼 Referer. 없으면 ""
	 */
	Page html(String url, String referer) {
		Response response = get(UrlGuard.parse(url), referer, true);
		if (response.status() != 200) {
			throw statusError(response.status());
		}
		if (!isHtml(response.contentType())) {
			throw failed("웹 페이지가 아닙니다(PDF나 그림 파일 등).");
		}
		return new Page(response.uri(), decode(response.body(), response.contentType()));
	}

	// ── 받기 ──────────────────────────────────────────────────────────

	private record Response(URI uri, int status, String contentType, byte[] body, String location) {
	}

	/** 넘어가라는 응답을 따라가며 받는다. page 면 넘어갈 때마다 robots.txt 를 본다. */
	private Response get(URI start, String referer, boolean page) {
		URI uri = start;
		for (int redirects = 0;; redirects++) {
			if (page && !robotsAllow(uri)) {
				throw blocked("이 사이트가 robots.txt 로 자동 수집을 막아 두었습니다.");
			}
			Response response = once(uri, referer, page);
			if (response.location() == null) {
				return response;
			}
			if (redirects == MAX_REDIRECTS) {
				throw failed("다른 주소로 너무 여러 번 넘어갑니다.");
			}
			try {
				uri = UrlGuard.parse(uri.resolve(response.location().strip()).toString());
			}
			catch (IllegalArgumentException ex) {
				throw failed("사이트가 알려 준 주소를 읽을 수 없습니다.");
			}
		}
	}

	/**
	 * 한 번 보낸다: 차례 기다리기, 주소 검사, 요청. 주소 검사는 보내기 바로 앞이다. 기다린 뒤에 검사해야 검사와 연결 사이가
	 * 밀리초로 짧다(앞에서 검사하면 기다리는 몇 초 동안 이름이 다른 주소로 바뀔 수 있다).
	 */
	private Response once(URI uri, String referer, boolean page) {
		waitTurn(uri.getHost().toLowerCase(Locale.ROOT));
		HttpRequest.Builder request = HttpRequest.newBuilder(uri)
			.timeout(this.settings.readTimeout())
			.header("User-Agent", this.userAgent)
			.header("Accept", page ? "text/html,application/xhtml+xml" : "text/plain,*/*;q=0.1")
			.header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.5")
			.GET();
		if (referer != null && !referer.isEmpty()) {
			request.header("Referer", referer);
		}
		UrlGuard.requirePublic(uri, this.settings.allowLoopbackForTests());
		CompletableFuture<HttpResponse<byte[]>> call = this.client.sendAsync(request.build(), (info) -> body(info, page));
		try {
			HttpResponse<byte[]> response = call.get(this.settings.connectTimeout().plus(this.settings.readTimeout()).toMillis(),
					TimeUnit.MILLISECONDS);
			String contentType = response.headers().firstValue("Content-Type").orElse("");
			return new Response(uri, response.statusCode(), contentType, response.body(), location(response));
		}
		catch (TimeoutException ex) {
			call.cancel(true);
			throw timedOut(uri);
		}
		catch (InterruptedException ex) {
			call.cancel(true);
			Thread.currentThread().interrupt();
			throw failed("읽기를 마치지 못했습니다.");
		}
		catch (ExecutionException ex) {
			Throwable cause = ex;
			while (cause != null) {
				if (cause instanceof TooLarge) {
					log.info("링크 읽기 실패: host={}, 본문이 {}바이트를 넘음", uri.getHost(), MAX_BYTES);
					throw failed("페이지가 너무 큽니다.");
				}
				if (cause instanceof HttpTimeoutException) {
					throw timedOut(uri);
				}
				cause = cause.getCause();
			}
			log.info("링크 읽기 실패: host={}, {}", uri.getHost(), ex.getCause().getClass().getSimpleName());
			throw failed("사이트에 연결하지 못했습니다.");
		}
	}

	/** 본문을 받을 응답만 받는다: 200 이고, 페이지면 HTML 일 때. 나머지는 상태와 머리만 쓴다. */
	private static BodySubscriber<byte[]> body(HttpResponse.ResponseInfo info, boolean page) {
		if (info.statusCode() != 200 || (page && !isHtml(info.headers().firstValue("Content-Type").orElse("")))) {
			return BodySubscribers.replacing(new byte[0]);
		}
		return new CappedBody(info.headers().firstValueAsLong("Content-Length").orElse(-1));
	}

	private static String location(HttpResponse<?> response) {
		int status = response.statusCode();
		boolean redirect = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
		return redirect ? response.headers().firstValue("Location").orElse(null) : null;
	}

	private void waitTurn(String host) {
		Duration wait = this.throttle.reserve(host)
			.orElseThrow(() -> failed("이 사이트에 읽기 요청이 몰려 있습니다. 잠시 뒤 다시 시도하거나"));
		if (wait.isZero()) {
			return;
		}
		try {
			Thread.sleep(wait);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw failed("읽기를 마치지 못했습니다.");
		}
	}

	/** 본문을 MAX_BYTES 까지만 모은다. 넘으면 받기를 끊는다. */
	private static final class CappedBody implements BodySubscriber<byte[]> {

		private final long declaredLength;

		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		private final CompletableFuture<byte[]> result = new CompletableFuture<>();

		private Flow.Subscription subscription;

		CappedBody(long declaredLength) {
			this.declaredLength = declaredLength;
		}

		@Override
		public CompletionStage<byte[]> getBody() {
			return this.result;
		}

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			this.subscription = subscription;
			if (this.declaredLength > MAX_BYTES) {
				tooLarge();
				return;
			}
			subscription.request(Long.MAX_VALUE);
		}

		@Override
		public void onNext(List<ByteBuffer> buffers) {
			for (ByteBuffer buffer : buffers) {
				if (this.result.isDone()) {
					return;
				}
				if (this.bytes.size() + buffer.remaining() > MAX_BYTES) {
					tooLarge();
					return;
				}
				byte[] chunk = new byte[buffer.remaining()];
				buffer.get(chunk);
				this.bytes.writeBytes(chunk);
			}
		}

		@Override
		public void onError(Throwable error) {
			this.result.completeExceptionally(error);
		}

		@Override
		public void onComplete() {
			this.result.complete(this.bytes.toByteArray());
		}

		private void tooLarge() {
			this.subscription.cancel();
			this.result.completeExceptionally(new TooLarge());
		}

	}

	private static final class TooLarge extends IOException {

	}

	// ── robots.txt ────────────────────────────────────────────────────

	private record CachedRobots(RobotsTxt rules, long expiresAt) {
	}

	private boolean robotsAllow(URI uri) {
		String origin = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT);
		CachedRobots cached = this.robots.get(origin);
		if (cached == null || System.nanoTime() - cached.expiresAt() > 0) {
			cached = new CachedRobots(loadRobots(origin), System.nanoTime() + ROBOTS_TTL.toNanos());
			if (this.robots.size() >= ROBOTS_CACHE_SIZE) {
				this.robots.clear();
			}
			this.robots.put(origin, cached);
		}
		String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? "/" : uri.getRawPath();
		String query = uri.getRawQuery();
		return cached.rules().allows(this.userAgent, (query == null || query.isEmpty()) ? path : path + "?" + query);
	}

	/**
	 * robots.txt 를 받는다(RFC 9309). 200 이면 읽고, 4xx 면 없는 것으로 보아 모두 받는다.
	 * 5xx 나 연결 실패면 사이트에 닿지 못한 것이라 받지 않는다. jobfit 은 이때도 받는다.
	 */
	private RobotsTxt loadRobots(String origin) {
		Response response = get(URI.create(origin + "/robots.txt"), "", false);
		if (response.status() == 200) {
			return RobotsTxt.parse(new String(response.body(), StandardCharsets.UTF_8));
		}
		if (response.status() >= 400 && response.status() < 500) {
			return RobotsTxt.ALLOW_ALL;
		}
		throw failed("사이트에 닿지 못했습니다. 잠시 뒤 다시 시도하거나");
	}

	// ── 응답 → 오류, 인코딩 ─────────────────────────────────────────────

	private static ApiException statusError(int status) {
		return switch (status) {
			case 401, 407 -> blocked("로그인이 필요한 페이지입니다.");
			case 403 -> blocked("사이트가 자동으로 읽는 것을 막았습니다.");
			case 429 -> blocked("사이트가 요청이 많다며 막았습니다. 잠시 뒤 다시 시도하거나");
			case 404, 410 -> failed("공고가 내려갔거나 주소가 틀렸습니다.");
			default -> (status >= 500) ? failed("사이트 쪽 오류로 읽지 못했습니다. 잠시 뒤 다시 시도하거나")
					: failed("이 링크는 읽지 못했습니다(응답 " + status + ").");
		};
	}

	private static boolean isHtml(String contentType) {
		String type = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
		return type.equals("text/html") || type.equals("application/xhtml+xml");
	}

	/**
	 * 받은 바이트를 글자로 푼다(jobfit 의 _decode). 머리의 charset(ISO-8859-1 은 기본값이라 믿지 않는다) → 앞 4KB 의 meta charset
	 * → UTF-8 → CP949 순으로 오류 없이 풀리는 것. 모두 안 되면 UTF-8 로 풀고 깨진 곳은 대체 글자로 둔다.
	 * EUC-KR 이라고 적힌 오래된 사이트는 그 확장인 CP949 로 푼다.
	 */
	static String decode(byte[] raw, String contentType) {
		List<String> candidates = new ArrayList<>();
		headerCharset(contentType).filter((cs) -> !cs.equalsIgnoreCase("iso-8859-1")).ifPresent(candidates::add);
		Matcher meta = META_CHARSET.matcher(new String(raw, 0, Math.min(raw.length, 4096), StandardCharsets.ISO_8859_1));
		if (meta.find()) {
			candidates.add(meta.group(1));
		}
		candidates.add("utf-8");
		candidates.add("cp949");
		for (String name : candidates) {
			try {
				return Charset.forName(korean(name))
					.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(raw))
					.toString();
			}
			catch (CharacterCodingException | IllegalCharsetNameException | UnsupportedCharsetException ex) {
				// 다음 후보
			}
		}
		return new String(raw, StandardCharsets.UTF_8);
	}

	private static Optional<String> headerCharset(String contentType) {
		for (String param : contentType.split(";")) {
			String[] pair = param.split("=", 2);
			if (pair.length == 2 && pair[0].strip().equalsIgnoreCase("charset")) {
				return Optional.of(Py.strip(pair[1].strip(), "'\""));
			}
		}
		return Optional.empty();
	}

	private static String korean(String charset) {
		return switch (charset.toLowerCase(Locale.ROOT)) {
			case "euc-kr", "euc_kr", "ks_c_5601-1987", "cp949", "ms949" -> "x-windows-949";
			default -> charset;
		};
	}

	private ApiException timedOut(URI uri) {
		log.info("링크 읽기 실패: host={}, 시간 초과", uri.getHost());
		return failed("사이트가 제때 응답하지 않았습니다.");
	}

	private static ApiException blocked(String why) {
		return new ApiException(PostingReadErrorCode.FETCH_BLOCKED, why + PASTE);
	}

	private static ApiException failed(String why) {
		return new ApiException(PostingReadErrorCode.FETCH_FAILED, why + PASTE);
	}

}
