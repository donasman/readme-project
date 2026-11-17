package com.readme.inboundmail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cloudflare 가 붙인 Authentication-Results(RFC 8601)의 검사 결과(메일 설계 M3). Spring 없이 돈다.
 *
 * <p>Worker 는 {@code message.headers.get("authentication-results")} 를 보낸다. 같은 이름의 헤더가 여럿이면 {@code ", "} 로 이어진
 * 값이다. 맨 앞의 것만 믿는다: 식별자가 {@code mx.cloudflare.net} 이어야 하고, 다음 헤더가 시작하는 곳({@code , <식별자>;})에서
 * 자른다. 나머지는 보낸 쪽이 넣었을 수 있다.
 */
record AuthResults(List<Result> results) {

	static final String CLOUDFLARE = "mx.cloudflare.net";

	static final AuthResults NONE = new AuthResults(List.of());

	private static final Pattern COMMENT = Pattern.compile("\\([^()]*\\)");

	/** 이어 붙은 다음 헤더의 시작: 쉼표 뒤의 식별자와 세미콜론 */
	private static final Pattern NEXT_HEADER = Pattern.compile(",\\s*[A-Za-z0-9.-]+(?:\\s+\\d+)?\\s*;");

	private static final Pattern PROPERTY = Pattern.compile("([A-Za-z]+\\.[A-Za-z0-9_-]+)=\"?([^\\s\";]+)\"?");

	/**
	 * 검사 하나. 예: {@code spf=pass smtp.mailfrom=name@gmail.com}.
	 *
	 * @param method     spf, dkim, dmarc 등. 소문자
	 * @param result     pass, fail, none 등. 소문자
	 * @param properties {@code smtp.mailfrom}, {@code header.d} 등 → 소문자 값
	 */
	record Result(String method, String result, Map<String, String> properties) {
	}

	static AuthResults parse(String header) {
		if (header == null) {
			return NONE;
		}
		String value = header;
		for (String previous = null; !value.equals(previous);) {
			previous = value;
			value = COMMENT.matcher(value).replaceAll(" ");
		}
		Matcher next = NEXT_HEADER.matcher(value);
		if (next.find()) {
			value = value.substring(0, next.start());
		}
		String[] parts = value.split(";");
		if (!parts[0].trim().equalsIgnoreCase(CLOUDFLARE)) {
			return NONE;
		}
		List<Result> results = new ArrayList<>();
		for (int i = 1; i < parts.length; i++) {
			String part = parts[i].trim();
			int eq = part.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String method = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
			String rest = part.substring(eq + 1).trim();
			String result = rest.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
			Map<String, String> properties = new HashMap<>();
			Matcher property = PROPERTY.matcher(rest);
			while (property.find()) {
				properties.putIfAbsent(property.group(1).toLowerCase(Locale.ROOT), property.group(2).toLowerCase(Locale.ROOT));
			}
			results.add(new Result(method, result, Map.copyOf(properties)));
		}
		return new AuthResults(List.copyOf(results));
	}

	/**
	 * method 의 검사가 pass 이고 그 property 의 도메인이 domain 과 맞는 것이 있으면 true. property 값이 주소면 @ 뒤가 도메인이다.
	 * 도메인은 같거나 한쪽이 다른 쪽의 하위 도메인이면 맞다고 본다.
	 */
	boolean passes(String method, String property, String domain) {
		return this.results.stream()
			.filter((r) -> r.method().equals(method) && r.result().equals("pass"))
			.map((r) -> r.properties().get(property))
			.anyMatch((value) -> value != null && aligned(domainOf(value), domain));
	}

	static String domainOf(String address) {
		int at = address.lastIndexOf('@');
		return ((at >= 0) ? address.substring(at + 1) : address).toLowerCase(Locale.ROOT);
	}

	private static boolean aligned(String a, String b) {
		return !a.isEmpty() && !b.isEmpty() && (a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a));
	}

}
