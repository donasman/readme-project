package com.readme.keyword;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 글 속 숫자 뽑기. 카드의 STAR 에 쓴 숫자가 근거 숫자(facts)에 있는 값인지 견주려고 쓴다. AI 없음.
 *
 * <p>jobfit 의 src/jobfit/numbers.py(extract, same_value, kor_to_int)를 옮겼다. 같은 글이면 같은 숫자가 나온다.
 * 정규식은 글자 그대로 옮겼고, Python 의 {@code (?P<이름>)}만 Java 의 {@code (?<이름>)}로 바꿨다.
 * 초안 검사에서 쓰는 등급(severity)은 아직 쓰는 곳이 없어 옮기지 않았다.
 *
 * <ul>
 * <li>같은 값으로 본다: 3천만 = 삼천만 = 30,000,000 = 3,000만, 4만 3천 = 43,000, 41k = 41,000. "약"·"여" 같은 어림말은 값과 무관하다</li>
 * <li>범위는 단위를 나눠 갖는다: 45~78% → 45%, 78%</li>
 * <li>숫자로 보지 않는다: 기술 이름 속 숫자(S3, OAuth2, p95), 버전(Java 17), 날짜(2025.03, 2025년 3월), 홀로 쓴 연도,
 * 시각(04:00), 관계(1:N), HTTP 상태 코드(429 오류), [확인 필요: …] 안, 줄 머리 번호(1. 2))</li>
 * </ul>
 *
 * <p>shortcut: \d 와 \s 는 ASCII 만 본다(Python 은 모든 문자의 숫자·공백). 먼저 NFKC 로 전각 숫자·공백을 바꾸므로 한국어 글에서는
 * 같다. 아랍 숫자처럼 NFKC 뒤에도 남는 다른 문자의 숫자가 든 자료가 나오면 그때 맞춘다.
 */
public final class Numbers {

	private static final Map<String, Integer> MULT = Map.of("백", 100, "천", 1_000, "만", 10_000, "십만", 100_000, "백만",
			1_000_000, "천만", 10_000_000, "억", 100_000_000, "k", 1_000);

	private static final Map<String, String> UNIT_ALIASES = Map.of("퍼센트", "%", "프로", "%", "번", "회", "달", "개월", "밀리초",
			"ms", "톤", "t");

	private static final String UNITS = "%|퍼센트|프로|ms|밀리초|초|분|시간|개월|주|일|년|배|[KMGT]B|원|건|명|인|회|번|개|가지|곳|차|위|팀|종|줄|대|달|톤|kg|t";

	private static final String NUMBER = "\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?";

	private static final String MULTS = "천만|백만|십만|만|천|억|백";

	/** 단위 낱말 바로 뒤에 다른 한글이 오면 단위가 아니다: "500 백엔드"의 백, "3만큼"의 만. 단위·"여"(5천여 명)는 괜찮다. */
	private static final String MULT_OK = "(?:" + MULTS + ")(?!(?!(?:" + UNITS + "|여))[가-힣])";

	/** 단위 뒤에 한글이 이어지면 조사·접미사일 때만 단위다: "3년간" "5명의"는 단위, "429 원인" "6대회"는 아니다. */
	private static final String PARTICLE_CHARS = "은는이가을를의에로으과와도만간째치당씩여쯤부까동며고나후전내량정보짜차께랑";

	private static final String UNIT = "(?<unit>" + UNITS + ")(?![가-힣](?<![" + PARTICLE_CHARS + "]))";

	/** 뒤에 단위가 오는지(버전·연도·상태 코드가 아니라 수량인지) 볼 때. */
	private static final String UNIT_AHEAD = "\\s*(?:(?:" + UNITS + ")(?![가-힣](?<![" + PARTICLE_CHARS + "]))|" + MULT_OK
			+ ")";

	private static final Pattern NUM = Pattern.compile("(?<![\\d.,])(?<num>" + NUMBER + ")(?!\\d)"
			+ "(?:\\s*(?<mult>" + MULT_OK + ")|(?<k>[kK]))?(?:\\s*" + UNIT + ")?(?![A-Za-z])");

	/** 범위: 45~78%, 3~6회. 뒤 숫자의 단위를 앞 숫자도 갖는다. */
	private static final Pattern RANGE = Pattern.compile("(?<![\\d.,])(?<a>" + NUMBER + ")\\s*[~–]\\s*(?<b>" + NUMBER
			+ ")(?!\\d)(?:\\s*(?<mult>" + MULT_OK + ")|(?<k>[kK]))?(?:\\s*" + UNIT + ")?(?![A-Za-z])");

	/** 섞인 한글 숫자: 4만 3천, 1억 2천만, 3만 5000 원. */
	private static final Pattern MIXED = Pattern.compile("(?<![\\d.,])(?<parts>(?:\\d+\\s*" + MULT_OK + "\\s*){2,}"
			+ "(?:\\d+(?![\\d.,]))?|(?:\\d+\\s*(?:억|만)(?![가-힣])\\s*)\\d+(?![\\d.,]))(?:\\s*" + UNIT + ")?(?![A-Za-z])");

	private static final Pattern MIXED_PART = Pattern.compile("(\\d+)\\s*(억|천만|백만|십만|만|천|백)?");

	/** 한글 숫자 낱말(삼천만). "3천만"의 "천만"처럼 아라비아 숫자 바로 뒤는 NUM 몫이다. */
	private static final Pattern KOR_NUM = Pattern.compile("(?<![가-힣\\d.,])(?<kor>[일이삼사오육칠팔구십백천만억]{2,})\\s*" + UNIT);

	private static final String KOR_DIGITS = "일이삼사오육칠팔구";

	private static final String YEAR = "(?:19|20)\\d{2}";

	private static final Pattern DATE = Pattern.compile(
			// 2025.03 · 2026.09.17 · 2026-10-28
			"(?<![\\d.,])(?<y1>" + YEAR + ")\\s*[.\\-/]\\s*\\d{1,2}(?:\\s*[.\\-/]\\s*\\d{1,2})?(?!\\d)"
					// 2025년 · 2025년 3월 5일 ("2025년 3개월"의 3은 남긴다)
					+ "|(?<![\\d.,])(?<y2>" + YEAR + ")\\s*년(?:\\s*\\d{1,2}\\s*월(?:\\s*\\d{1,2}\\s*일)?)?"
					// 3월 · 3월 5일
					+ "|(?<![\\d.,])\\d{1,2}\\s*월(?:\\s*\\d{1,2}\\s*일)?"
					// 09-15 (앞자리 0이 있는 월-일만)
					+ "|(?<![\\d.\\-/])(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])(?![\\d\\-])"
					// 단위 없이 홀로 쓴 연도: 2026 – 현재
					+ "|(?<![\\d.,])(?<y3>19[89]\\d|20[0-3]\\d)(?![\\d.,]|" + UNIT_AHEAD + ")");

	private static final Pattern TIME = Pattern.compile("(?<![\\d:])\\d{1,2}:\\d{2}(?::\\d{2})?(?![\\d:])");

	/** Python 의 \w 는 한글도 낱말 글자로 본다. 그래서 이것만 UNICODE_CHARACTER_CLASS 다. */
	private static final Pattern RELATION = Pattern.compile("(?<![\\w])(?:1|N|M|n|m):(?:1|N|M|n|m)(?![\\w])",
			Pattern.UNICODE_CHARACTER_CLASS);

	private static final String HTTP_CODES = "200|201|202|204|301|302|304|400|401|403|404|405|408|409|410|413|415|422|429|500|501|502|503|504";

	private static final Pattern HTTP_WORDS = Pattern
		.compile("HTTP|status|상태\\s*코드|응답|오류|에러|error|[1-5]xx|Too Many|Rate Limit", Pattern.CASE_INSENSITIVE);

	private static final Pattern HTTP_CODE = Pattern
		.compile("(?<![\\d.,])(?:" + HTTP_CODES + ")(?![\\d.,]|" + UNIT_AHEAD + ")");

	/** 문장에 오류·응답 같은 말이 없어도 조사가 바로 붙으면 코드다: "429는", "500을 막음". */
	private static final Pattern HTTP_CODE_PARTICLE = Pattern.compile("(?<![\\d.,])(?:" + HTTP_CODES
			+ ")(?=(?:은|는|이|가|의|에|에는|에서|을|를|로|도|만|이어도|일 때)(?![가-힣]{2}))");

	/** 문맥 없이도 이름인 것: 429(요청 과다 코드), 앞자리 0인 두 자리(단계 번호 01 · 04). */
	private static final Pattern ALWAYS_LABEL = Pattern.compile("(?<![\\d.,])(?:429|0\\d)(?![\\d.,]|" + UNIT_AHEAD + ")");

	private static final Pattern SENTENCE = Pattern.compile("[^.!?\\n]+[.!?]?");

	private static final Pattern TECH = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z][A-Za-z.+#/-]*\\d[A-Za-z0-9.+#/-]*");

	private static final Pattern CONFIRM = Pattern.compile("\\[확인\\s*필요[^\\]]*\\]");

	/** Python 처럼 \n 만 줄 끝으로 본다(UNIX_LINES). */
	private static final Pattern LINE_NUMBER = Pattern.compile("^\\s*\\d{1,2}[.)]\\s", Pattern.MULTILINE | Pattern.UNIX_LINES);

	/** 뒤에 버전 숫자가 붙는 이름. 키워드 사전의 영문 표기에 더해 쓴다. */
	private static final List<String> EXTRA_VERSION_NAMES = List.of("JDK", "OpenJDK", "JRE", "Prisma", "Gemini", "GPT",
			"Claude", "Llama", "Gemma", "Mistral", "Qwen", "DeepSeek", "Ubuntu", "Debian", "CentOS", "Rocky Linux",
			"Amazon Linux", "Windows", "Windows Server", "macOS", "iOS", "Android", "Vue", "Angular", "Svelte", "Nuxt",
			"Next.js", "React", "React Native", "Node", "Node.js", "Deno", "Bun", "Python", "Java", "Kotlin", "Swift", "Go",
			"Rust", "PHP", "Ruby", "Rails", "Scala", "Dart", "Flutter", "C#", ".NET", "Spring", "Spring Boot",
			"Spring Batch", "Spring Security", "Hibernate", "MyBatis", "Django", "Flask", "FastAPI", "Express", "NestJS",
			"PostgreSQL", "MySQL", "MariaDB", "Oracle", "MongoDB", "Redis", "Elasticsearch", "Kafka", "SQLite",
			"SQL Server", "Tomcat", "Nginx", "Apache", "Jenkins", "Docker", "Kubernetes", "Terraform", "Gradle", "Maven",
			"Webpack", "Vite", "TypeScript", "JavaScript", "ECMAScript", "ES", "HTML", "CSS", "Bootstrap", "Tailwind",
			"Jest", "Vitest", "JUnit", "TensorFlow", "PyTorch", "CUDA", "Unity", "Unreal", "Electron", "Excel", "Office",
			"TLS", "SSL", "OAuth", "HTTP", "IPv");

	private Numbers() {
	}

	/** 숫자 하나. value 는 단위 배수까지 푼 값, unit 은 정규화한 단위(없으면 ""), raw 는 글에 쓰인 그대로다. */
	public record NumToken(double value, String unit, String raw) {
	}

	/** 뽑은 숫자들(글의 순서가 아니라 섞인 한글 숫자, 한글 숫자, 범위, 나머지 순)과 날짜에서 읽은 연도. */
	public record Extracted(List<NumToken> numbers, List<Integer> years) {
	}

	public static Extracted extract(String text) {
		String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
		s = mask(s, CONFIRM);
		s = mask(s, LINE_NUMBER);
		s = mask(s, Versions.PATTERN);
		s = mask(s, TECH);
		List<Integer> years = new ArrayList<>();
		for (Matcher m = DATE.matcher(s); m.find();) {
			String year = firstNonNull(m.group("y1"), m.group("y2"), m.group("y3"));
			if (year != null) {
				years.add(Integer.parseInt(year));
			}
		}
		s = mask(s, DATE);
		s = mask(s, TIME);
		s = mask(s, RELATION);
		s = maskHttpCodes(s);
		List<NumToken> numbers = new ArrayList<>();
		for (Matcher m = MIXED.matcher(s); m.find();) {
			double value = mixedValue(m.group("parts"));
			if (value != 0) {
				numbers.add(new NumToken(value, unit(m.group("unit")), m.group().strip()));
			}
		}
		s = mask(s, MIXED);
		for (Matcher m = KOR_NUM.matcher(s); m.find();) {
			long value = korToInt(m.group("kor"));
			if (value != 0) {
				numbers.add(new NumToken(value, unit(m.group("unit")), m.group().strip()));
			}
		}
		s = mask(s, KOR_NUM);
		for (Matcher m = RANGE.matcher(s); m.find();) {
			int mult = mult(m);
			String unit = unit(m.group("unit"));
			String rawUnit = (m.group("unit") != null) ? m.group("unit") : "";
			for (String part : List.of("a", "b")) {
				String raw = m.group(part);
				numbers.add(new NumToken(Double.parseDouble(raw.replace(",", "")) * mult, unit, raw + rawUnit));
			}
		}
		s = mask(s, RANGE);
		for (Matcher m = NUM.matcher(s); m.find();) {
			double value = Double.parseDouble(m.group("num").replace(",", "")) * mult(m);
			numbers.add(new NumToken(value, unit(m.group("unit")), m.group().strip()));
		}
		return new Extracted(List.copyOf(numbers), List.copyOf(years));
	}

	/** facts 에 같은 값이 있나. 단위가 같거나 한쪽에 단위가 없으면 값만 본다. */
	public static boolean sameValue(NumToken a, List<NumToken> facts) {
		for (NumToken f : facts) {
			if (Math.abs(f.value() - a.value()) <= 1e-9 * Math.max(1.0, Math.abs(a.value()))
					&& (f.unit().equals(a.unit()) || f.unit().isEmpty() || a.unit().isEmpty())) {
				return true;
			}
		}
		return false;
	}

	/** 삼천만 → 30000000. 숫자 낱말이 아니면 0. */
	static long korToInt(String s) {
		Map<Character, Integer> small = Map.of('십', 10, '백', 100, '천', 1000);
		Map<Character, Integer> big = Map.of('만', 10_000, '억', 100_000_000);
		if (s.chars().noneMatch((ch) -> small.containsKey((char) ch) || big.containsKey((char) ch))) {
			return 0;
		}
		long total = 0;
		long section = 0;
		long num = 0;
		for (char ch : s.toCharArray()) {
			int digit = KOR_DIGITS.indexOf(ch);
			if (digit >= 0) {
				num = digit + 1;
			}
			else if (small.containsKey(ch)) {
				section += ((num != 0) ? num : 1) * small.get(ch);
				num = 0;
			}
			else if (big.containsKey(ch)) {
				section += num;
				total += ((section != 0) ? section : 1) * big.get(ch);
				section = 0;
				num = 0;
			}
			else {
				return 0;
			}
		}
		return total + section + num;
	}

	/**
	 * "4만 3천" → 43000, "1억 2천만" → 120000000, "3만 5000" → 35000. 큰 단위가 앞에 와야 한다("3만 2억"은 0).
	 *
	 * <p>double 로 더한다. jobfit 도 끝에 float 로 바꾸고, long 이면 아주 큰 수("1만 12345678901234567890")에서 넘치거나 실패한다.
	 * shortcut: 2^53 을 넘는 수는 마지막 자리가 jobfit(정수로 다 더한 뒤 바꿈)과 다를 수 있다, 그런 값을 견줘야 하면 BigInteger 로 더한다.
	 */
	private static double mixedValue(String parts) {
		double total = 0;
		double section = 0;
		Integer lastBig = null;
		for (Matcher m = MIXED_PART.matcher(parts); m.find();) {
			double n = Double.parseDouble(m.group(1));
			String mult = (m.group(2) != null) ? m.group(2) : "";
			if (mult.equals("억") || mult.equals("만")) {
				int big = MULT.get(mult);
				if (lastBig != null && big >= lastBig) {
					return 0;
				}
				total += (section + n) * big;
				section = 0;
				lastBig = big;
			}
			else if (mult.equals("천만") || mult.equals("백만") || mult.equals("십만")) {
				total += n * MULT.get(mult);
			}
			else if (!mult.isEmpty()) {
				section += n * MULT.get(mult);
			}
			else {
				section += n;
			}
		}
		return total + section;
	}

	private static int mult(Matcher m) {
		if (m.group("k") != null) {
			return 1_000;
		}
		return (m.group("mult") != null) ? MULT.get(m.group("mult")) : 1;
	}

	/** HTTP 상태 코드: 오류·응답·HTTP 같은 말이 있는 문장 안의 단위 없는 200·429·500 등만 가린다. */
	private static String maskHttpCodes(String s) {
		String sentences = replace(s, SENTENCE,
				(sentence) -> HTTP_WORDS.matcher(sentence.group()).find() ? mask(sentence.group(), HTTP_CODE) : sentence.group());
		return mask(mask(sentences, HTTP_CODE_PARTICLE), ALWAYS_LABEL);
	}

	private static String unit(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		String u = raw.replace(" ", "");
		return UNIT_ALIASES.getOrDefault(u, u);
	}

	/** 맞은 자리를 같은 길이의 공백으로 덮는다. 뒤의 정규식이 그 자리를 다시 읽지 않게. */
	private static String mask(String s, Pattern pattern) {
		return replace(s, pattern, (m) -> " ".repeat(m.group().length()));
	}

	private static String replace(String s, Pattern pattern, Function<MatchResult, String> replacement) {
		return pattern.matcher(s).replaceAll((m) -> Matcher.quoteReplacement(replacement.apply(m)));
	}

	@SafeVarargs
	private static <T> T firstNonNull(T... values) {
		for (T value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/** 이름 + 버전 숫자. 뒤에 단위가 붙으면 버전이 아니다("Java 3년"의 3은 남긴다). 사전을 처음 쓸 때 만든다. */
	private static final class Versions {

		static final Pattern PATTERN = pattern();

		private static Pattern pattern() {
			Set<String> names = new TreeSet<>(EXTRA_VERSION_NAMES);
			names.addAll(KeywordDictionary.standard().englishNames());
			// 긴 이름부터. 길이가 같으면 이름순(jobfit 과 같은 순서)
			String alternatives = names.stream()
				.sorted(Comparator.comparingInt(String::length).reversed())
				.map(Pattern::quote)
				.collect(Collectors.joining("|"));
			return Pattern.compile("(?<![A-Za-z])(?:" + alternatives + ")\\s*v?\\d+(?:\\.\\d+)*(?![\\d.]|" + UNIT_AHEAD + ")",
					Pattern.CASE_INSENSITIVE);
		}

	}

}
