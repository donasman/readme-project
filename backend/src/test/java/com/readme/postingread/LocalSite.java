package com.readme.postingread;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 테스트가 띄우는 내 PC 의 웹 사이트(127.0.0.1, 빈 포트). 경로마다 응답을 정해 두고, 몇 번 불렸는지와 마지막 요청 머리를 기억한다.
 * 정하지 않은 경로는 404 다(robots.txt 를 정하지 않으면 robots.txt 가 없는 사이트가 된다).
 *
 * <p>읽어 오기는 루프백 주소를 막으므로, 이 사이트를 읽으려면 allow-loopback-for-tests 를 켠 PageFetcher 를 쓴다.
 */
final class LocalSite implements AutoCloseable {

	private final HttpServer server;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final Map<String, Route> routes = new ConcurrentHashMap<>();

	private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

	private final Map<String, Headers> lastHeaders = new ConcurrentHashMap<>();

	/**
	 * @param delay     머리를 보내기 전에 기다리는 시간
	 * @param bodyDelay 머리를 보낸 뒤 본문 앞에서 기다리는 시간
	 */
	private record Route(int status, Map<String, String> headers, byte[] body, Duration delay, boolean chunked,
			Duration bodyDelay) {

		Route(int status, Map<String, String> headers, byte[] body, Duration delay, boolean chunked) {
			this(status, headers, body, delay, chunked, Duration.ZERO);
		}

	}

	private LocalSite() {
		try {
			this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		this.server.createContext("/", this::handle);
		this.server.setExecutor(this.executor);
		this.server.start();
	}

	static LocalSite start() {
		return new LocalSite();
	}

	String url(String path) {
		return "http://127.0.0.1:" + this.server.getAddress().getPort() + path;
	}

	LocalSite html(String path, String html) {
		return respond(path, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
	}

	LocalSite robots(String text) {
		return respond("/robots.txt", 200, "text/plain", text.getBytes(StandardCharsets.UTF_8));
	}

	LocalSite redirect(String path, String location) {
		this.routes.put(path, new Route(302, Map.of("Location", location), new byte[0], Duration.ZERO, false));
		return this;
	}

	LocalSite respond(String path, int status, String contentType, byte[] body) {
		this.routes.put(path, new Route(status, Map.of("Content-Type", contentType), body, Duration.ZERO, false));
		return this;
	}

	/** 머리를 보내기 전에 delay 만큼 기다린다. */
	LocalSite slow(String path, Duration delay) {
		this.routes.put(path, new Route(200, Map.of("Content-Type", "text/html"), "<p>늦은 페이지</p>".getBytes(StandardCharsets.UTF_8),
				delay, false));
		return this;
	}

	/** 머리는 바로 보내고 본문은 delay 뒤에 보낸다. HttpClient 의 시간 한도는 머리까지만 보므로 PageFetcher 의 전체 한도가 끊어야 한다. */
	LocalSite slowBody(String path, Duration delay) {
		this.routes.put(path, new Route(200, Map.of("Content-Type", "text/html"), "<p>늦은 본문</p>".getBytes(StandardCharsets.UTF_8),
				Duration.ZERO, false, delay));
		return this;
	}

	/** 길이를 알리지 않고(chunked) 보낸다. */
	LocalSite chunked(String path, String contentType, byte[] body) {
		this.routes.put(path, new Route(200, Map.of("Content-Type", contentType), body, Duration.ZERO, true));
		return this;
	}

	int hits(String path) {
		AtomicInteger count = this.hits.get(path);
		return (count != null) ? count.get() : 0;
	}

	Headers lastHeaders(String path) {
		return this.lastHeaders.get(path);
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		this.hits.computeIfAbsent(path, (p) -> new AtomicInteger()).incrementAndGet();
		this.lastHeaders.put(path, exchange.getRequestHeaders());
		Route route = this.routes.getOrDefault(path,
				new Route(404, Map.of("Content-Type", "text/html"), "<p>없음</p>".getBytes(StandardCharsets.UTF_8), Duration.ZERO, false));
		try {
			if (!route.delay().isZero()) {
				Thread.sleep(route.delay());
			}
			route.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
			exchange.sendResponseHeaders(route.status(), route.chunked() ? 0 : (route.body().length == 0 ? -1 : route.body().length));
			if (route.body().length > 0) {
				try (OutputStream out = exchange.getResponseBody()) {
					out.flush();
					if (!route.bodyDelay().isZero()) {
						Thread.sleep(route.bodyDelay());
					}
					out.write(route.body());
				}
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		catch (IOException ex) {
			// 받는 쪽이 끊었다(너무 큰 본문, 시간 초과)
		}
		finally {
			exchange.close();
		}
	}

	@Override
	public void close() {
		this.server.stop(0);
		this.executor.shutdownNow();
	}

}
