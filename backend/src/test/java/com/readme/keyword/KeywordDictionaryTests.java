package com.readme.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 키워드 사전. jobfit 의 tests/test_experiences.py(태그 정규화), tests/test_match.py(포함 관계), tests/test_clean.py(글에서 찾기)에서
 * 사전을 쓰는 부분을 옮겼고, 아래 표는 jobfit 의 keywords.py 를 Python 3.12 로 직접 돌려(python -I) 리드미 사전(resources/dictionary)에서
 * 얻은 값이다.
 *
 * <p>옮기지 않은 것: test_keywords_do_not_overmatch 의 forms 한 줄(forms 를 옮기지 않았다).
 */
class KeywordDictionaryTests {

	private static final KeywordDictionary DICTIONARY = KeywordDictionary.standard();

	// ── test_experiences.py, test_match.py, test_clean.py ──────────────────

	@Test
	void keywordsDoNotOvermatch() {
		Map<String, Integer> found = DICTIONARY
			.find("C# (.Net) 과 C++ 경험. 코드 리뷰와 인터뷰. JavaScript 아님 Java. PostgreSQL 과 SQL 튜닝. Node.js");
		assertThat(found).doesNotContainKeys("C", "Vue")
			.containsEntry("Java", 1)
			.containsEntry("JavaScript", 1)
			.containsEntry("SQL", 1)
			.containsEntry("PostgreSQL", 1)
			.containsEntry("C#", 1)
			.containsEntry("C++", 1);
		// 긴 표기가 먼저. 'Spring Boot'·'스프링부트' 안의 'Spring'·'스프링'은 따로 세지 않는다
		assertThat(DICTIONARY.find("Spring Boot와 스프링부트, 그리고 Spring")).containsExactly(entry("Spring Boot", 2), entry("Spring", 1));
	}

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

	/** 많이 나온 순, 같으면 대표 표기의 글자 순이라 Map 의 글자로 순서까지 견준다. */
	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
			// 낱말의 일부는 아니다(Java ≠ JavaScript, Javadoc)
			"JavaScript 아님 Java | {Java=1, JavaScript=1}", "Javadoc 과 JavaFX | {}",
			// 영문 바로 뒤의 한글 조사는 막지 않는다. 한 글자 표기(C)만 한글이 붙으면 뺀다
			"Java를 쓰고 Spring Boot로 만든 API와 Docker에 | {API=1, Docker=1, Java=1, Spring Boot=1}",
			"C언어, C 언어, C로, C/C++ | {C=2, C++=1}", "Go로 만든 서버와 Golang | {Go=2}",
			// 대소문자를 가리지 않고 다른 표기는 대표 표기로 센다
			"JAVA, java, K8S, k8s, 쿠버네티스 | {Kubernetes=3, Java=2}",
			"스프링부트, 스프링 부트, SpringBoot, springboot | {Spring Boot=4}",
			// 한글 표기는 글자 그대로 찾되 긴 것이 먼저다
			"자바스크립트와 자바 | {Java=1, JavaScript=1}",
			// 앞의 . # + 와 뒤의 영문·숫자·# + 는 막고, 뒤의 . _ - / 와 앞의 _ 는 막지 않는다
			"Node.js 와 Vue.js, ASP.NET, .NET 6 | {.NET=2, Node.js=1, Vue=1}",
			"Java17, Java 17, Python3, Python 3.12 | {Java=1, Python=1}", "Java. Kotlin, Python! | {Java=1, Kotlin=1, Python=1}",
			"\"#Java +Java .Java _Java\" | {Java=1}", "Java_8 Java-8 Java/Spring | {Java=3, Spring=1}",
			"CI/CD 와 CICD, CI·CD | {CI/CD=3}", "scikit-learn 과 sklearn | {scikit-learn=2}",
			// Python 의 re.I 처럼 켈빈 기호(K)와 점 없는 i(ı)도 같은 글자로 본다
			"K8s ıos Lınux | {Kubernetes=1, Linux=1, iOS=1}",
			"Python Docker Python Java 데이터베이스 Git | {Python=2, Docker=1, Git=1, Java=1, 데이터베이스=1}" })
	void findSameAsJobfit(String text, String expected) {
		assertThat(DICTIONARY.find(text)).hasToString(expected);
	}

	@Test
	void blankTextHasNoKeywords() {
		assertThat(DICTIONARY.find("")).isEmpty();
		assertThat(DICTIONARY.find("   \n\t")).isEmpty();
	}

	/** 공고 페이지(1MB)만 한 글도 한 번 읽는 시간에 끝난다. 찾은 자리를 가릴 때 글 전체를 다시 만들지 않는다(jobfit 은 만든다). */
	@Test
	void longTextTakesLinearTime() {
		String text = "Java, Spring Boot와 k8s 경험. ".repeat(40_000) + " ".repeat(50_000);
		Map<String, Integer> found = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> DICTIONARY.find(text));
		assertThat(found).containsExactly(entry("Java", 40_000), entry("Kubernetes", 40_000), entry("Spring Boot", 40_000));
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
