package com.readme.postingread;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * robots.txt 의 규칙. jobfit 의 src/jobfit/fetch/robots.py 는 Python 의 urllib.robotparser 에 맡기므로 그 3.13 판(RFC 9309)을
 * 옮겼다: 가장 긴 규칙이 이기고, 길이가 같으면 Allow 가 이기고, * 와 끝의 $ 를 읽고, 같은 이름의 그룹은 합친다.
 * Crawl-delay, Request-rate, Sitemap 은 읽기만 하고 쓰지 않는다. 받기와 캐시는 {@link PageFetcher} 가 한다.
 */
final class RobotsTxt {

	/** robots.txt 가 없을 때(4xx). */
	static final RobotsTxt ALLOW_ALL = new RobotsTxt(Map.of());

	private static final Pattern NOT_QUERY_SEPARATORS = Pattern.compile("[^=&]+");

	private static final Pattern NOT_PATTERN_SEPARATORS = Pattern.compile("[^*$]+");

	private static final Pattern NOT_PATTERN_QUERY_SEPARATORS = Pattern.compile("[^=&*$]+");

	private static final Pattern STARS = Pattern.compile("[*]{2,}");

	private static final Pattern DOLLARS = Pattern.compile("[$][$*]+");

	/** 그룹 이름(소문자) → 그룹. 적힌 순서대로. */
	private final Map<String, Group> groups;

	private RobotsTxt(Map<String, Group> groups) {
		this.groups = groups;
	}

	static RobotsTxt parse(String text) {
		Map<String, Group> groups = new LinkedHashMap<>();
		int state = 0; // 0: 처음, 1: User-agent 를 봄, 2: 규칙을 봄
		Group group = new Group(new ArrayList<>(), new ArrayList<>());
		for (String raw : Py.lines(text)) {
			int comment = raw.indexOf('#');
			String line = Py.strip((comment >= 0) ? raw.substring(0, comment) : raw);
			int colon = line.indexOf(':');
			if (line.isEmpty() || colon < 0) {
				continue;
			}
			String field = Py.strip(line.substring(0, colon)).toLowerCase(Locale.ROOT);
			String value = Py.strip(line.substring(colon + 1));
			switch (field) {
				case "user-agent" -> {
					if (state == 2) {
						add(groups, group);
						group = new Group(new ArrayList<>(), new ArrayList<>());
					}
					group.agents().add(value);
					state = 1;
				}
				case "allow", "disallow" -> {
					if (state != 0) {
						state = 2;
						Rule rule = Rule.of(value, "allow".equals(field));
						if (rule != null) {
							group.rules().add(rule);
						}
					}
				}
				case "crawl-delay", "request-rate" -> state = (state != 0) ? 2 : 0;
				default -> {
					// sitemap 과 모르는 줄
				}
			}
		}
		if (state != 0) {
			add(groups, group);
		}
		return new RobotsTxt(groups);
	}

	/**
	 * 이 UA 가 이 주소를 받아도 되는가.
	 *
	 * @param target 요청할 경로와 쿼리("/jobs/view?rec_idx=1"). # 뒤는 요청에 실리지 않으므로 넣지 않는다
	 */
	boolean allows(String userAgent, String target) {
		String url = normalizeUri(target);
		if (url.isEmpty()) {
			url = "/";
		}
		if ("/robots.txt".equals(url)) {
			return true;
		}
		Group group = find(userAgent);
		return group == null || group.allows(url);
	}

	private Group find(String userAgent) {
		Group exact = this.groups.get(userAgent.toLowerCase(Locale.ROOT));
		if (exact != null) {
			return exact;
		}
		for (Group group : this.groups.values()) {
			if (group.appliesTo(userAgent)) {
				return group;
			}
		}
		return this.groups.get("*");
	}

	private static void add(Map<String, Group> groups, Group group) {
		for (String agent : group.agents()) {
			groups.merge(agent.toLowerCase(Locale.ROOT), group, RobotsTxt::merge);
		}
	}

	/** 같은 이름의 그룹 둘. 규칙은 이어 붙이고, 이름은 둘 다에 있는 것만 남긴다(Python 의 merge_entries). */
	private static Group merge(Group first, Group second) {
		List<String> agents = first.agents().stream().filter(second.agents()::contains).toList();
		List<Rule> rules = new ArrayList<>(first.rules());
		rules.addAll(second.rules());
		return new Group(agents, rules);
	}

	private record Group(List<String> agents, List<Rule> rules) {

		/** UA 의 이름 부분("readmefetcher")에 그룹 이름이 들어 있으면 이 그룹이다. * 는 따로 본다. */
		boolean appliesTo(String userAgent) {
			String name = userAgent.split("/", -1)[0].toLowerCase(Locale.ROOT);
			return this.agents.stream().anyMatch((agent) -> !"*".equals(agent) && name.contains(agent.toLowerCase(Locale.ROOT)));
		}

		boolean allows(String url) {
			int best = -1;
			boolean allowed = true;
			for (Rule rule : this.rules) {
				int length = rule.matchLength(url);
				if (length > 0) {
					if (length > best) {
						best = length;
						allowed = rule.allow();
					}
					else if (length == best && !allowed) {
						allowed = rule.allow();
					}
				}
			}
			return allowed;
		}

	}

	/**
	 * Allow·Disallow 한 줄.
	 *
	 * @param matcher 경로에 * 가 있을 때만
	 */
	private record Rule(String path, boolean allow, boolean fullMatch, Pattern matcher) {

		/** 가운데에 $ 가 있는 줄은 규칙이 아니라 null. 빈 Disallow 는 모두 받아도 된다는 뜻이다. */
		static Rule of(String value, boolean allow) {
			boolean allowance = allow || value.isEmpty();
			String path = STARS.matcher(value).replaceAll("*");
			path = DOLLARS.matcher(path).replaceAll("\\$");
			path = normalizePattern(path);
			boolean fullMatch = path.endsWith("$");
			path = Py.rstrip(path, "$");
			if (path.contains("$")) {
				return null;
			}
			Pattern matcher = path.contains("*") ? Pattern.compile(translate(path), Pattern.DOTALL) : null;
			return new Rule(path, allowance, fullMatch, matcher);
		}

		/** 맞으면 맞은 길이 + 1, 아니면 0. */
		int matchLength(String url) {
			if (this.matcher != null) {
				Matcher m = this.matcher.matcher(url);
				boolean found = this.fullMatch ? m.matches() : m.lookingAt();
				return found ? m.end() + 1 : 0;
			}
			boolean found = this.fullMatch ? url.equals(this.path) : url.startsWith(this.path);
			return found ? this.path.length() + 1 : 0;
		}

		/** "/a*b*c" → "/a(?>.*?b).*c". 가운데 조각은 되돌아가지 않는 묶음이다(Python 의 translate_pattern). */
		private static String translate(String path) {
			String[] parts = path.split("\\*", -1);
			StringBuilder regex = new StringBuilder(Pattern.quote(parts[0]));
			for (int i = 1; i < parts.length - 1; i++) {
				regex.append("(?>.*?").append(Pattern.quote(parts[i])).append(')');
			}
			return regex.append(".*").append(Pattern.quote(parts[parts.length - 1])).toString();
		}

	}

	// ── Python 의 normalize·normalize_uri·normalize_pattern ────────────────

	/** 경로와, 쿼리의 이름·값을 따로 다시 인코딩한다. ? = & 는 그대로 둔다. */
	private static String normalizeUri(String target) {
		int question = target.indexOf('?');
		if (question < 0) {
			return normalize(target);
		}
		return normalize(target.substring(0, question)) + "?" + replace(NOT_QUERY_SEPARATORS, target.substring(question + 1));
	}

	/** 규칙의 경로. * 와 $ 는 그대로 둔다. */
	private static String normalizePattern(String path) {
		int question = path.indexOf('?');
		if (question < 0) {
			return replace(NOT_PATTERN_SEPARATORS, path);
		}
		return replace(NOT_PATTERN_SEPARATORS, path.substring(0, question)) + "?"
				+ replace(NOT_PATTERN_QUERY_SEPARATORS, path.substring(question + 1));
	}

	private static String replace(Pattern pattern, String text) {
		return pattern.matcher(text).replaceAll((m) -> Matcher.quoteReplacement(normalize(m.group())));
	}

	/**
	 * quote(unquote(s)): %XX 는 바이트로 풀고, 영문·숫자·_.-~ 와 / 만 두고 나머지 바이트는 대문자 %XX 로 쓴다.
	 * 그래서 %2f 와 / 가 같고, 한글과 그 퍼센트 인코딩이 같다.
	 */
	private static String normalize(String text) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '%' && i + 2 < text.length() && isHex(text.charAt(i + 1)) && isHex(text.charAt(i + 2))) {
				bytes.write(Integer.parseInt(text.substring(i + 1, i + 3), 16));
				i += 3;
				continue;
			}
			int codePoint = text.codePointAt(i);
			bytes.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
			i += Character.charCount(codePoint);
		}
		StringBuilder out = new StringBuilder();
		for (byte b : bytes.toByteArray()) {
			int c = b & 0xff;
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "_.-~/".indexOf(c) >= 0) {
				out.append((char) c);
			}
			else {
				out.append('%').append(String.format("%02X", c));
			}
		}
		return out.toString();
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

}
