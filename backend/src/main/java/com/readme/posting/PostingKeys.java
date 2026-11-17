package com.readme.posting;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고키(postings.posting_key). 같은 공고를 알아보는 키다(설계 그룹·공고 가정 3).
 *
 * <p>jobfit 의 src/jobfit/jobkey.py(derive, normalize_url)를 옮겼다. 같은 입력이면 jobfit 과 같은 키가 나온다.
 * 다른 점 둘: 사이트 호스트를 점 경계로 본다(SITE_RULES 의 설명). # 뒤가 / 로 시작하면 키에 넣는다(normalizeUrl 의 설명).
 * Python 의 urlsplit, parse_qsl, urlencode 가 하는 일을 그대로 따라 한다(아래의 split, parseQuery, quotePlus).
 * 게시처 판정(kind)은 아직 쓰는 곳이 없어 옮기지 않았다.
 *
 * <ul>
 * <li>{@code <사이트>:<공고번호>}: 사람인·잡코리아·원티드·점핏·리멤버·프로그래머스·토스</li>
 * <li>{@code url:<호스트/경로?남은 값>}: 모르는 사이트. 추적값과 # 뒤를 뺀 주소. # 뒤가 / 로 시작하면 {@code #/…}를 붙인다</li>
 * <li>{@code local:<해시 8자>}: 링크가 없을 때. 회사명|직무|마감일의 SHA-1</li>
 * </ul>
 */
public final class PostingKeys {

	/**
	 * 먼저 맞는 규칙을 쓴다. 점핏은 jumpit.saramin.co.kr 로도 열리므로 사람인보다 먼저 본다.
	 * 호스트는 그 사이트 자체이거나 그 하위 도메인이어야 한다({@code (^|\.)}). jobfit 은 "그 이름으로 끝나기만" 보아서
	 * fakesaramin.co.kr 도 사람인 공고키가 되는데, 리드미에서는 공고키가 같으면 다른 그룹이 그 공고 본체(원문 링크 포함)를
	 * 함께 쓰므로 남의 호스트가 사이트 공고를 가로챌 수 있다. 진짜 사이트 주소의 키는 jobfit 과 같다.
	 */
	private static final List<SiteRule> SITE_RULES = List.of(
			new SiteRule("jumpit", "(^|\\.)jumpit\\.(co\\.kr|saramin\\.co\\.kr)$", "/position/(\\d+)"),
			new SiteRule("jobkorea", "(^|\\.)jobkorea\\.co\\.kr$", "/Recruit/GI_Read/(\\d+)", "[?&]Gno=(\\d+)"),
			new SiteRule("saramin", "(^|\\.)saramin\\.co\\.kr$", "[?&]rec_idx=(\\d+)"),
			new SiteRule("wanted", "(^|\\.)wanted\\.co\\.kr$", "/wd/(\\d+)"),
			new SiteRule("remember", "(^|\\.)(remember|rememberapp)\\.co\\.kr$", "/posting/(\\d+)"),
			new SiteRule("programmers", "(^|\\.)programmers\\.co\\.kr$", "/job_positions/(\\d+)"));

	private static final Pattern TOSS_HOST = Pattern.compile("(^|\\.)toss\\.(im|com)$");

	/** 토스는 한 job_id 에 포지션이 여럿이라 둘을 합쳐야 공고 하나다. */
	private static final Pattern TOSS_JOB = Pattern.compile("[?&]job_id=([^&#]+)");

	private static final Pattern TOSS_SUB = Pattern.compile("[?&]sub_position_id=([^&#]+)");

	private static final Pattern HOST_PREFIX = Pattern.compile("^(www|m)\\.");

	private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+\\-.]*:");

	/** 같은 공고를 다른 주소로 보이게 만드는 추적용 쿼리 값. */
	private static final Set<String> TRACKING_EXACT = Set.of("ref", "t_content", "isMypage", "gclid", "fbclid", "yclid",
			"igshid");

	private static final List<String> TRACKING_PREFIX = List.of("utm_", "t_ref");

	private PostingKeys() {
	}

	/**
	 * 링크가 있으면 링크에서, 없으면 회사명·직무·마감일에서 키를 만든다.
	 *
	 * @param deadline 마감일 "YYYY-MM-DD". 날짜가 없으면 ""
	 */
	public static PostingKey derive(String url, String company, String title, String deadline) {
		String link = strip(url);
		if (!link.isEmpty()) {
			return fromUrl(link);
		}
		String seed = String.join("|", strip(company), strip(title), strip(deadline));
		return new PostingKey("local:" + sha1Hex(seed).substring(0, 8), "local", "");
	}

	/**
	 * 추적값과 # 뒤를 빼고 호스트의 www. 이나 m. 을 뗀 "호스트/경로?남은 값".
	 *
	 * <p>jobfit 과 다른 점: # 뒤가 / 로 시작하면(해시로 화면을 바꾸는 채용 사이트의 {@code #/jobs/101}) 공고마다 다른
	 * 주소이므로 경로처럼 다듬어 붙인다. jobfit 은 이것도 빼서 그 사이트의 공고가 모두 같은 키가 된다.
	 */
	public static String normalizeUrl(String url) {
		UrlParts parts = split(strip(url));
		String host = HOST_PREFIX.matcher(parts.netloc().toLowerCase(Locale.ROOT)).replaceFirst("");
		return host + pathAndQuery(parts.path(), parts.query()) + hashRoute(parts.fragment());
	}

	/** 끝의 / 를 뗀 경로와, 추적값을 뺀 쿼리. */
	private static String pathAndQuery(String path, String query) {
		List<String> kept = new ArrayList<>();
		for (String[] pair : parseQuery(query)) {
			if (!isTracking(pair[0])) {
				kept.add(quotePlus(pair[0]) + "=" + quotePlus(pair[1]));
			}
		}
		String keptQuery = String.join("&", kept);
		return path.replaceAll("/+$", "") + (keptQuery.isEmpty() ? "" : "?" + keptQuery);
	}

	/** # 뒤가 / 로 시작하면 "#/경로?남은 값". 아니면(문서 안의 위치, #!) 또는 첫 화면(#/)이면 "". */
	private static String hashRoute(String fragment) {
		if (!fragment.startsWith("/")) {
			return "";
		}
		int question = fragment.indexOf('?');
		String route = (question >= 0) ? pathAndQuery(fragment.substring(0, question), fragment.substring(question + 1))
				: pathAndQuery(fragment, "");
		if (route.isEmpty()) {
			return "";
		}
		return "#" + (route.startsWith("/") ? route : "/" + route);
	}

	private static PostingKey fromUrl(String url) {
		String host = split(url).netloc().toLowerCase(Locale.ROOT);
		for (SiteRule rule : SITE_RULES) {
			if (!rule.host().matcher(host).find()) {
				continue;
			}
			for (Pattern number : rule.numbers()) {
				Matcher m = number.matcher(url);
				if (m.find()) {
					return new PostingKey(rule.site() + ":" + m.group(1), rule.site(), m.group(1));
				}
			}
		}
		if (TOSS_HOST.matcher(host).find()) {
			Matcher job = TOSS_JOB.matcher(url);
			Matcher sub = TOSS_SUB.matcher(url);
			if (job.find() && sub.find()) {
				String number = job.group(1) + "-" + sub.group(1);
				return new PostingKey("toss:" + number, "toss", number);
			}
		}
		return new PostingKey("url:" + normalizeUrl(url), "url", "");
	}

	private static boolean isTracking(String name) {
		return TRACKING_EXACT.contains(name) || TRACKING_PREFIX.stream().anyMatch(name::startsWith);
	}

	// ── Python urllib.parse 를 따라 한 것 ──────────────────────────────────

	/** urlsplit: 스킴, //호스트, 경로, ?쿼리, #조각(# 를 뺀 뒤쪽 전부)으로 나눈다. 잘못된 주소에서도 오류 없이 나눈다. */
	static UrlParts split(String url) {
		String rest = url.replaceAll("[\\t\\r\\n]", "");
		Matcher scheme = SCHEME.matcher(rest);
		if (scheme.find()) {
			rest = rest.substring(scheme.end());
		}
		String netloc = "";
		if (rest.startsWith("//")) {
			int end = rest.length();
			for (char delimiter : new char[] { '/', '?', '#' }) {
				int at = rest.indexOf(delimiter, 2);
				if (at >= 0) {
					end = Math.min(end, at);
				}
			}
			netloc = rest.substring(2, end);
			rest = rest.substring(end);
		}
		String fragment = "";
		int hash = rest.indexOf('#');
		if (hash >= 0) {
			fragment = rest.substring(hash + 1);
			rest = rest.substring(0, hash);
		}
		String query = "";
		int question = rest.indexOf('?');
		if (question >= 0) {
			query = rest.substring(question + 1);
			rest = rest.substring(0, question);
		}
		return new UrlParts(netloc, rest, query, fragment);
	}

	/** parse_qsl(keep_blank_values=True): & 로 나누고, = 가 없으면 값은 "", + 는 공백, % 는 풀어 읽는다. */
	private static List<String[]> parseQuery(String query) {
		List<String[]> pairs = new ArrayList<>();
		for (String field : query.split("&", -1)) {
			if (field.isEmpty()) {
				continue;
			}
			int equals = field.indexOf('=');
			String name = (equals >= 0) ? field.substring(0, equals) : field;
			String value = (equals >= 0) ? field.substring(equals + 1) : "";
			pairs.add(new String[] { unquote(name.replace('+', ' ')), unquote(value.replace('+', ' ')) });
		}
		return pairs;
	}

	/** unquote(errors="replace"): %XX 를 바이트로 모아 UTF-8 로 읽는다. 잘못된 % 는 그대로 둔다. */
	private static String unquote(String text) {
		StringBuilder out = new StringBuilder();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '%' && isHex(text, i + 1) && isHex(text, i + 2)) {
				bytes.write(Integer.parseInt(text.substring(i + 1, i + 3), 16));
				i += 3;
				continue;
			}
			if (bytes.size() > 0) {
				out.append(bytes.toString(StandardCharsets.UTF_8));
				bytes.reset();
			}
			out.append(c);
			i++;
		}
		if (bytes.size() > 0) {
			out.append(bytes.toString(StandardCharsets.UTF_8));
		}
		return out.toString();
	}

	private static boolean isHex(String text, int index) {
		return index < text.length() && Character.digit(text.charAt(index), 16) >= 0 && text.charAt(index) < 128;
	}

	/** quote_plus: 영문·숫자와 _.-~ 만 두고 나머지는 UTF-8 바이트의 %XX, 공백은 +. */
	private static String quotePlus(String text) {
		StringBuilder out = new StringBuilder();
		for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xff;
			if (c == ' ') {
				out.append('+');
			}
			else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.'
					|| c == '-' || c == '~') {
				out.append((char) c);
			}
			else {
				out.append('%').append(HexFormat.of().withUpperCase().toHexDigits((byte) c));
			}
		}
		return out.toString();
	}

	private static String strip(String text) {
		return (text != null) ? text.strip() : "";
	}

	private static String sha1Hex(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	record UrlParts(String netloc, String path, String query, String fragment) {
	}

	private record SiteRule(String site, Pattern host, List<Pattern> numbers) {

		SiteRule(String site, String host, String... numbers) {
			this(site, Pattern.compile(host), List.of(numbers).stream().map(Pattern::compile).toList());
		}

	}

}
