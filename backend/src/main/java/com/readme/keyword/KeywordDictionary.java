package com.readme.keyword;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import org.springframework.core.io.ClassPathResource;

/**
 * 키워드 사전(resources/dictionary). 대표 표기 하나에 여러 표기가 붙고, 어떤 표기든 대표 표기로 바꾼다(k8s → Kubernetes).
 *
 * <p>jobfit 의 src/jobfit/clean/keywords.py(KeywordDict 의 load, normalize, expand, scan, find)를 옮겼다. 같은 사전이면 같은 값이 나온다.
 * 이력서 표현을 공고 표기로 맞추는 forms 는 쓰는 곳이 없어 옮기지 않았다.
 * 영문은 대소문자를 가리지 않는다. 사전에 없는 낱말은 그대로 둔다.
 *
 * <ul>
 * <li>keywords.txt: 한 줄에 대표 표기 하나. # 뒤는 설명</li>
 * <li>synonyms.yaml: 대표 표기: [다른 표기들]</li>
 * <li>implies.yaml: 이것을 썼으면 저것도 쓴 것(Spring Boot → Spring). 카드를 공고에 맞출 때 넓히는 데만 쓴다</li>
 * </ul>
 */
public final class KeywordDictionary {

	private static final String DIRECTORY = "dictionary/";

	/** 앞뒤 글자를 보고 찾는 표기: 영문·숫자로 끝나고 영문·숫자와 . + # / _ 공백 - 로만 된 것. */
	private static final Pattern ENGLISH_FORM = Pattern.compile("[A-Za-z0-9.+#/_ -]*[A-Za-z0-9]");

	private static final int IGNORE_CASE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	/** 대표 표기 → 표기들(대표 표기가 맨 앞). keywords.txt, synonyms.yaml 의 순서다. */
	private final Map<String, List<String>> variants;

	/** 대표 표기 → 그것을 썼으면 함께 쓴 것. 양쪽 모두 대표 표기다. */
	private final Map<String, List<String>> implies;

	/** 글에서 찾는 순서: 긴 표기부터, 길이가 같으면 소문자의 글자 순(jobfit 의 _ordered). */
	private final List<Form> forms;

	private KeywordDictionary(Map<String, List<String>> variants, Map<String, List<String>> implies) {
		this.variants = variants;
		this.implies = implies;
		this.forms = variants.entrySet()
			.stream()
			.flatMap((entry) -> entry.getValue().stream().map((form) -> Map.entry(form, entry.getKey())))
			.sorted(Comparator.comparingInt((Map.Entry<String, String> pair) -> -pair.getKey().codePointCount(0, pair.getKey().length()))
				.thenComparing((pair) -> lower(pair.getKey())))
			.map((pair) -> new Form(pair.getValue(), pattern(pair.getKey()), fold(pair.getKey()).toString()))
			.toList();
	}

	/** 저장소의 사전. 처음 부를 때 한 번 읽는다. */
	public static KeywordDictionary standard() {
		return Standard.DICTIONARY;
	}

	/**
	 * 자격증 사전(certificates.yaml). 대표 표기와 다른 표기만 읽는다(normalize, contains, find). 관련 역량은 아직 읽지 않는다.
	 * 키워드 사전과 따로인 이유는 그 파일의 머리말에 있다.
	 */
	public static KeywordDictionary certificates() {
		return Certificates.DICTIONARY;
	}

	/** 어떤 표기든 대표 표기로. 앞뒤 공백을 지우고, 사전에 없으면 그대로. */
	public String normalize(String token) {
		String text = token.strip();
		String canonical = canonicalOf(text);
		return (canonical != null) ? canonical : text;
	}

	/** 대표 표기 전부. keywords.txt, synonyms.yaml 의 순서다. 공고 분석의 프롬프트가 싣는다(공고 설계 4.7). */
	public List<String> canonicals() {
		return List.copyOf(this.variants.keySet());
	}

	/**
	 * 글에 이 낱말이 있는지, 사전 낱말과 같은 경계 규칙으로 본다(jobfit 의 word_pattern). 사전 밖의 낱말(카드 태그 '배치' 등)을
	 * 찾을 때 쓴다. 부분 문자열로 찾으면 Java 가 JavaScript 에 걸린다.
	 */
	public static boolean mentions(String text, String word) {
		return pattern(word).matcher(text).find();
	}

	/** 사전에 있는 표기(대표 표기나 다른 표기)인지. 대소문자는 가리지 않는다. */
	public boolean contains(String token) {
		return canonicalOf(token.strip()) != null;
	}

	/** 태그에 포함 관계를 끝까지 따라가 더한다. 원래 태그가 앞, 더한 것은 뒤. 같은 낱말은 대소문자를 가리지 않고 한 번만. */
	public List<String> expand(List<String> tags) {
		List<String> out = new ArrayList<>(new LinkedHashSet<>(tags));
		Set<String> seen = new HashSet<>();
		out.forEach((tag) -> seen.add(lower(tag)));
		for (int i = 0; i < out.size(); i++) {
			for (String implied : this.implies.getOrDefault(out.get(i), List.of())) {
				if (seen.add(lower(implied))) {
					out.add(implied);
				}
			}
		}
		return out;
	}

	/**
	 * 글에 나온 사전 낱말(대표 표기)과 횟수. 많이 나온 순, 같으면 대표 표기의 글자 순이다(jobfit 의 find).
	 * 긴 표기부터 찾고 찾은 자리를 공백으로 가려서 'Spring Boot' 안의 'Spring', '자바스크립트' 안의 '자바'를 따로 세지 않는다.
	 */
	public Map<String, Integer> find(String text) {
		StringBuilder rest = new StringBuilder(text);
		StringBuilder folded = fold(text);
		Map<String, Integer> counts = new HashMap<>();
		for (Form form : this.forms) {
			// 정규식은 접은 글에 그 표기가 있는 자리에서만 맞춰 본다. 모든 자리에서 맞춰 보면 표기 수(약 600)만큼 글을 읽어 1MB 에 15초 걸렸다
			Matcher matcher = form.pattern().matcher(rest).useTransparentBounds(true);
			// 찾은 자리는 시작·끝 int 둘로만 둔다. MatchResult 는 하나에 약 190바이트라 1MB 에 짧은 표기가 많으면 수백 MB 가 된다
			int[] spans = new int[16];
			int size = 0;
			for (int at = folded.indexOf(form.folded()); at >= 0; at = folded.indexOf(form.folded(), at + 1)) {
				if (matcher.region(at, rest.length()).lookingAt()) {
					if (size == spans.length) {
						spans = Arrays.copyOf(spans, size * 2);
					}
					spans[size++] = at;
					spans[size++] = matcher.end();
					at = matcher.end() - 1;
				}
			}
			// jobfit 처럼 한 표기를 다 찾은 뒤에 가린다(".NET.NET"의 둘째는 앞 글자 T 때문에 아니다). 가리는 일은 찾은 글자 수만큼이라
			// 글 길이의 제곱이 되지 않는다(jobfit 은 글을 다시 만든다)
			for (int s = 0; s < size; s += 2) {
				for (int i = spans[s]; i < spans[s + 1]; i++) {
					rest.setCharAt(i, ' ');
					folded.setCharAt(i, ' ');
				}
			}
			if (size > 0) {
				counts.merge(form.canonical(), size / 2, Integer::sum);
			}
		}
		Map<String, Integer> sorted = new LinkedHashMap<>();
		counts.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.forEach((entry) -> sorted.put(entry.getKey(), entry.getValue()));
		return sorted;
	}

	/**
	 * 태그 입력 후보. 친 글자와 같은 표기, 그 글자로 시작하는 표기, 그 글자가 든 표기의 순이고, 같은 순위면 사전 순서다.
	 * 대표 표기 하나는 한 번만 나온다. 다른 표기에 걸렸으면 그 표기를 note 에 적는다("k8s의 대표 표기").
	 */
	List<KeywordSuggestion> suggest(String query, int limit) {
		String q = lower(query.strip());
		if (q.isEmpty()) {
			return List.of();
		}
		record Hit(int rank, int order, KeywordSuggestion suggestion) {
		}
		List<Hit> hits = new ArrayList<>();
		int order = 0;
		for (Map.Entry<String, List<String>> entry : this.variants.entrySet()) {
			String canonical = entry.getKey();
			Hit best = null;
			for (String variant : entry.getValue()) {
				String v = lower(variant);
				int rank = v.equals(q) ? 0 : v.startsWith(q) ? 1 : v.contains(q) ? 2 : -1;
				if (rank >= 0 && (best == null || rank < best.rank())) {
					String note = variant.equals(canonical) ? null : variant + "의 대표 표기";
					best = new Hit(rank, order, new KeywordSuggestion(canonical, note));
				}
			}
			if (best != null) {
				hits.add(best);
			}
			order++;
		}
		return hits.stream()
			.sorted(Comparator.comparingInt(Hit::rank).thenComparingInt(Hit::order))
			.limit(limit)
			.map(Hit::suggestion)
			.toList();
	}

	/** 사전의 영문 표기(영문·숫자와 . + # 공백 - 로만 된 것). 숫자 읽기가 "Java 17"의 17 같은 버전을 숫자로 보지 않는 데 쓴다. */
	Set<String> englishNames() {
		Set<String> names = new LinkedHashSet<>();
		this.variants.values().forEach((forms) -> forms.stream()
			.filter((form) -> form.matches("[A-Za-z.][A-Za-z0-9.+# -]*"))
			.forEach(names::add));
		return names;
	}

	/** jobfit 과 같이 사전 순서대로 보고 처음 맞는 대표 표기를 쓴다. */
	private String canonicalOf(String text) {
		String low = lower(text);
		for (Map.Entry<String, List<String>> entry : this.variants.entrySet()) {
			for (String variant : entry.getValue()) {
				if (lower(variant).equals(low)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	private static String lower(String text) {
		return text.toLowerCase(Locale.ROOT);
	}

	/**
	 * jobfit 의 _pattern. 영문·숫자 표기는 앞이 영문·숫자·#·+·. 이 아니고(JS ≠ Node.js) 뒤가 영문·숫자·#·+ 가 아닐 때만
	 * 맞는다(Java ≠ JavaScript, C ≠ C# ≠ C++). 뒤의 '.'은 문장 끝일 수 있어 막지 않는다. 한 글자(C)는 한글이 바로 붙은 것('C동')도
	 * 뺀다. 한글이 든 표기와 C#·C++ 는 글자 그대로 찾는다. Python 의 re.I 처럼 유니코드로 대소문자를 가리지 않는다(켈빈 기호 K 도 k).
	 */
	private static Pattern pattern(String form) {
		String quoted = Pattern.quote(form);
		if (ENGLISH_FORM.matcher(form).matches()) {
			String tail = (form.length() == 1) ? "(?![A-Za-z0-9#+가-힣])" : "(?![A-Za-z0-9#+])";
			return Pattern.compile("(?<![A-Za-z0-9#+.])" + quoted + tail, IGNORE_CASE);
		}
		return Pattern.compile(quoted, IGNORE_CASE);
	}

	/** 정규식(CASE_INSENSITIVE | UNICODE_CASE)이 글자를 견주는 꼴. 글자마다 바꾸므로 자리가 원문과 같다. */
	private static StringBuilder fold(String text) {
		StringBuilder folded = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			folded.append(Character.toLowerCase(Character.toUpperCase(text.charAt(i))));
		}
		return folded;
	}

	/** 표기 하나: 대표 표기, 찾는 정규식, 후보 자리를 찾을 접은 표기. */
	private record Form(String canonical, Pattern pattern, String folded) {
	}

	// ── 읽기 ───────────────────────────────────────────────────────────

	private static KeywordDictionary read() {
		List<String> canonical = new ArrayList<>();
		for (String line : text("keywords.txt").split("\\R")) {
			String name = line.split("#", 2)[0].strip();
			if (!name.isEmpty() && !canonical.contains(name)) {
				canonical.add(name);
			}
		}
		Map<String, List<String>> synonyms = new LinkedHashMap<>();
		yaml("synonyms.yaml").forEach((key, value) -> {
			String name = String.valueOf(key);
			synonyms.put(name, values(value));
			if (!canonical.contains(name)) {
				canonical.add(name);
			}
		});
		Map<String, List<String>> variants = new LinkedHashMap<>();
		for (String name : canonical) {
			List<String> forms = new ArrayList<>();
			forms.add(name);
			forms.addAll(synonyms.getOrDefault(name, List.of()));
			variants.put(name, List.copyOf(forms));
		}
		KeywordDictionary withoutImplies = new KeywordDictionary(variants, Map.of());
		Map<String, List<String>> implies = new LinkedHashMap<>();
		yaml("implies.yaml").forEach((key, value) -> implies.put(withoutImplies.normalize(String.valueOf(key)),
				values(value).stream().map(withoutImplies::normalize).toList()));
		return new KeywordDictionary(variants, implies);
	}

	/** 목록이 아니면 한 개짜리 목록으로. 빈 값은 뺀다(jobfit 의 {@code [str(a) for a in alts if a]}). */
	private static List<String> values(Object value) {
		List<?> list = (value instanceof List<?> items) ? items : List.of(Objects.requireNonNullElse(value, ""));
		return list.stream().filter(KeywordDictionary::isPresent).map(String::valueOf).toList();
	}

	private static boolean isPresent(Object value) {
		return value != null && !"".equals(value) && !Boolean.FALSE.equals(value);
	}

	private static Map<?, ?> yaml(String file) {
		Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text(file));
		return (loaded instanceof Map<?, ?> map) ? map : Map.of();
	}

	private static String text(String file) {
		try (InputStream in = new ClassPathResource(DIRECTORY + file).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("키워드 사전을 읽지 못했다: " + file, ex);
		}
	}

	private static final class Standard {

		static final KeywordDictionary DICTIONARY = read();

	}

	private static final class Certificates {

		static final KeywordDictionary DICTIONARY = readCertificates();

		/** 대표 표기: {다른 표기: [...], 관련 역량: [...]} */
		private static KeywordDictionary readCertificates() {
			Map<String, List<String>> variants = new LinkedHashMap<>();
			yaml("certificates.yaml").forEach((key, value) -> {
				List<String> forms = new ArrayList<>();
				forms.add(String.valueOf(key));
				if (value instanceof Map<?, ?> fields) {
					forms.addAll(values(fields.get("다른 표기")));
				}
				variants.put(String.valueOf(key), List.copyOf(forms));
			});
			return new KeywordDictionary(variants, Map.of());
		}

	}

}
