package com.readme.keyword;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import org.springframework.core.io.ClassPathResource;

/**
 * 키워드 사전(resources/dictionary). 대표 표기 하나에 여러 표기가 붙고, 어떤 표기든 대표 표기로 바꾼다(k8s → Kubernetes).
 *
 * <p>jobfit 의 src/jobfit/clean/keywords.py(KeywordDict 의 load, normalize, expand)를 옮겼다. 같은 사전이면 같은 값이 나온다.
 * 글에서 키워드를 찾는 scan·find·forms 는 공고 분석에서 쓰므로 아직 옮기지 않았다.
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

	/** 대표 표기 → 표기들(대표 표기가 맨 앞). keywords.txt, synonyms.yaml 의 순서다. */
	private final Map<String, List<String>> variants;

	/** 대표 표기 → 그것을 썼으면 함께 쓴 것. 양쪽 모두 대표 표기다. */
	private final Map<String, List<String>> implies;

	private KeywordDictionary(Map<String, List<String>> variants, Map<String, List<String>> implies) {
		this.variants = variants;
		this.implies = implies;
	}

	/** 저장소의 사전. 처음 부를 때 한 번 읽는다. */
	public static KeywordDictionary standard() {
		return Standard.DICTIONARY;
	}

	/** 어떤 표기든 대표 표기로. 앞뒤 공백을 지우고, 사전에 없으면 그대로. */
	public String normalize(String token) {
		String text = token.strip();
		String canonical = canonicalOf(text);
		return (canonical != null) ? canonical : text;
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

}
