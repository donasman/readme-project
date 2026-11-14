package com.readme.keyword;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 키워드 사전. jobfit 의 tests/test_experiences.py(태그 정규화)와 tests/test_match.py(포함 관계)에서 사전을 쓰는 부분을 옮겼고,
 * 아래 표는 jobfit 의 keywords.py 를 Python 3.12 로 직접 돌려 리드미 사전(resources/dictionary)에서 얻은 값이다.
 *
 * <p>옮기지 않은 것: test_keywords_do_not_overmatch(글에서 키워드 찾기. 공고 분석을 옮길 때 같이 옮긴다).
 */
class KeywordDictionaryTests {

	private static final KeywordDictionary DICTIONARY = KeywordDictionary.standard();

	// ── test_experiences.py, test_match.py ─────────────────────────────────

	@Test
	void tagsAreNormalized() {
		// test_types_and_tags_are_normalized 의 태그 부분: 스프링부트 → Spring Boot, 파이썬과 Python 은 하나
		assertThat(List.of("스프링부트", "파이썬", "Python").stream().map(DICTIONARY::normalize).distinct())
			.containsExactly("Spring Boot", "Python");
	}

	@Test
	void implicationGoesOneWayAndFollowsChains() {
		assertThat(DICTIONARY.expand(List.of(DICTIONARY.normalize("스프링부트")))).containsExactly("Spring Boot", "Spring", "Java");
		assertThat(DICTIONARY.expand(List.of("Next.js"))).containsExactly("Next.js", "React", "JavaScript");
		assertThat(DICTIONARY.expand(List.of("Java"))).containsExactly("Java"); // Java를 썼다고 Spring을 쓴 건 아니다
	}

	// ── keywords.py 를 직접 돌려 얻은 값 ────────────────────────────────────

	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
			// 대소문자를 가리지 않고, 앞뒤 공백을 지운다
			"k8s | Kubernetes", "K8S | Kubernetes", "\" 스프링 부트 \" | Spring Boot", "springboot | Spring Boot",
			"\"  java  \" | Java", "자바 | Java", "c# | C#", "C | C",
			// 여러 낱말로 된 다른 표기, 짧은 다른 표기
			"SQL Server | MSSQL", "Spring Data JPA | JPA", "ES | Elasticsearch", "elt | ETL", "db | 데이터베이스",
			"react.js | React",
			// 리드미가 더한 사전
			"스마트 공장 | 스마트팩토리", "OPC | OPC UA",
			// 사전에 없으면 그대로(Node 홀로는 사전에 없다)
			"Node | Node", "unknownTool | unknownTool", "\"\" | \"\"" })
	void normalizeSameAsJobfit(String token, String expected) {
		assertThat(DICTIONARY.normalize(token)).isEqualTo(expected);
	}

	@Test
	void expandSameAsJobfit() {
		assertThat(DICTIONARY.expand(List.of("NestJS"))).containsExactly("NestJS", "Node.js", "TypeScript", "JavaScript");
		assertThat(DICTIONARY.expand(List.of("Hibernate", "JPA"))).containsExactly("Hibernate", "JPA", "Java");
		assertThat(DICTIONARY.expand(List.of("PostgreSQL"))).containsExactly("PostgreSQL", "SQL", "데이터베이스");
		assertThat(DICTIONARY.expand(List.of("GitHub Actions", "Express")))
			.containsExactly("GitHub Actions", "Express", "CI/CD", "Node.js", "JavaScript");
		assertThat(DICTIONARY.expand(List.of("Spring Boot", "Spring Boot"))).containsExactly("Spring Boot", "Spring", "Java");
		assertThat(DICTIONARY.expand(List.of("MES"))).containsExactly("MES", "스마트팩토리");
		assertThat(DICTIONARY.expand(List.of("SCADA"))).containsExactly("SCADA", "공장 자동화", "설비 모니터링");
		assertThat(DICTIONARY.expand(List.of("없는태그"))).containsExactly("없는태그");
		// 포함 관계는 대표 표기에만 걸린다. 더할 것은 대소문자를 가리지 않고 이미 있으면 넣지 않는다
		assertThat(DICTIONARY.expand(List.of("java", "Spring"))).containsExactly("java", "Spring");
	}

	// ── 리드미에서 더한 것 ───────────────────────────────────────────────

	@Test
	void containsKnowsEveryForm() {
		assertThat(DICTIONARY.contains("Kubernetes")).isTrue();
		assertThat(DICTIONARY.contains(" k8s ")).isTrue();
		assertThat(DICTIONARY.contains("없는태그")).isFalse();
	}

	@Test
	void suggestionsPutTheSameFormFirstThenPrefixThenContains() {
		assertThat(DICTIONARY.suggest("k8s", 10)).containsExactly(new KeywordSuggestion("Kubernetes", "k8s의 대표 표기"));
		assertThat(DICTIONARY.suggest(" K8S ", 10)).containsExactly(new KeywordSuggestion("Kubernetes", "k8s의 대표 표기"));
		// 대표 표기에 걸리면 note 가 없다. 같은 표기 → 그 글자로 시작 → 그 글자가 듦, 같으면 사전 순서
		assertThat(DICTIONARY.suggest("java", 10)).containsExactly(new KeywordSuggestion("Java", null),
				new KeywordSuggestion("JavaScript", null));
		assertThat(DICTIONARY.suggest("스프링", 10)).containsExactly(new KeywordSuggestion("Spring", "스프링의 대표 표기"),
				new KeywordSuggestion("Spring Boot", "스프링부트의 대표 표기"),
				new KeywordSuggestion("Spring Batch", "스프링배치의 대표 표기"),
				new KeywordSuggestion("Spring Security", "스프링시큐리티의 대표 표기"));
		assertThat(DICTIONARY.suggest("sql", 10)).startsWith(new KeywordSuggestion("SQL", null));
	}

	@Test
	void suggestionsAreCappedAndBlankGivesNone() {
		assertThat(DICTIONARY.suggest("a", 10)).hasSize(10);
		assertThat(DICTIONARY.suggest("  ", 10)).isEmpty();
		assertThat(DICTIONARY.suggest("없는낱말", 10)).isEmpty();
	}

}
