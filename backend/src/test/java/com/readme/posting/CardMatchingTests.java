package com.readme.posting;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.readme.keyword.KeywordDictionary;
import com.readme.posting.CardMatching.CardMatch;
import com.readme.posting.CardMatching.MatchCard;
import com.readme.posting.CardMatching.ProfileFacts;
import com.readme.posting.CardMatching.RequirementCard;
import com.readme.posting.CardMatching.Requirement;
import com.readme.posting.CardMatching.RequirementResult;
import com.readme.posting.CardMatching.Result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * jobfit tests/test_match.py 에서 옮겼다: test_requirements_score_and_card_ranking(전체 점수 빼고),
 * test_dictionary_tags_keep_word_boundaries_and_role_words_are_ignored, test_implied_tags_count_for_the_language,
 * test_implication_goes_one_way_and_follows_chains, test_rdbms_requirement_accepts_other_databases. 카드 id 는 숫자다.
 */
class CardMatchingTests {

	private static final KeywordDictionary KD = KeywordDictionary.standard();

	private static final PostingAnalysis ANALYSIS = analysis(
			List.of("Python 기반 API 개발", "SQL 성능 최적화", "Docker 경험", "피드백을 받아들이는 태도"),
			List.of("Kotlin·Spring Boot 개발", "배치 처리 경험"), List.of("Python", "Docker", "Kotlin", "Redis"));

	private static final long ETL = 1;

	private static final long API = 2;

	private static final long DOC = 3;

	private static final List<MatchCard> CARDS = List.of(new MatchCard(ETL, "ETL 파이프라인", "", List.of("파이썬", "Airflow", "배치")),
			new MatchCard(API, "주문 API 개선", "", List.of("Java", "스프링부트", "성능 최적화", "Docker")),
			new MatchCard(DOC, "온보딩 문서", "", List.of("협업", "문서화")));

	@Test
	void requirementsAndCardRanking() {
		Map<String, Requirement> requirements = requirements(ANALYSIS, CARDS);

		assertThat(requirements.get("Python 기반 API 개발").cards()).containsExactly(ETL);
		assertThat(requirements.get("SQL 성능 최적화").cards()).containsExactly(API); // '성능 최적화' 사전 낱말
		assertThat(requirements.get("피드백을 받아들이는 태도").terms()).isEmpty(); // 태그로 판단할 수 없는 요건
		assertThat(requirements.get("배치 처리 경험").cards()).containsExactly(ETL); // 사전에 없는 카드 태그도 글에 있으면 낱말
		assertThat(requirements.get("Kotlin·Spring Boot 개발").cards()).containsExactly(API); // Kotlin 은 없지만 Spring Boot

		Result result = CardMatching.match(ANALYSIS, CARDS, ProfileFacts.EMPTY, KD);

		// api: 필수 2(SQL, Docker)·우대 1·키워드 1(Docker) = 9, etl: 필수 1·우대 1·키워드 1(Python) = 6, doc: 0
		assertThat(result.matches()).extracting(CardMatch::cardId).containsExactly(API, ETL);
		assertThat(result.matches().get(0).overlap()).containsExactly("성능 최적화", "Docker", "Spring Boot");
		assertThat(result.matches().get(1).overlap()).containsExactly("Python", "배치");
		assertThat(result.unmatched()).isEmpty(); // 판단할 수 있는 요건은 모두 맞췄다. '태도'는 판단할 수 없다
	}

	@Test
	void requirementsNoCardCoversAreUnmatched() {
		Result result = CardMatching.match(analysis(List.of("Go 언어 개발", "성실함"), List.of("Kubernetes 운영"), List.of()),
				CARDS, ProfileFacts.EMPTY, KD);

		assertThat(result.matches()).isEmpty();
		assertThat(result.unmatched()).containsExactly("Go 언어 개발", "Kubernetes 운영");
	}

	@Test
	void dictionaryTagsKeepWordBoundariesAndRoleWordsAreIgnored() {
		List<MatchCard> cards = List.of(new MatchCard(1, "자바", "", List.of("Java", "백엔드")));
		PostingAnalysis analysis = analysis(List.of("JavaScript 또는 TypeScript 코드 작성", "백엔드 개발 경험"), List.of(),
				List.of("백엔드", "Java"));

		Map<String, Requirement> requirements = requirements(analysis, cards);

		assertThat(requirements.get("JavaScript 또는 TypeScript 코드 작성").cards()).isEmpty(); // Java 태그가 JavaScript 에 걸리지 않는다
		assertThat(requirements.get("백엔드 개발 경험").terms()).isEmpty(); // 직무 이름은 낱말로 치지 않는다
		assertThat(CardMatching.match(analysis, cards, ProfileFacts.EMPTY, KD).matches()).singleElement()
			.extracting(CardMatch::overlap)
			.isEqualTo(List.of("Java")); // 키워드도 직무 이름은 뺀다
	}

	@Test
	void impliedTagsCountForTheLanguage() {
		List<MatchCard> cards = List.of(new MatchCard(1, "알고리즘 스터디", "", List.of("Java")),
				new MatchCard(2, "공공 포털 유지보수", "", List.of("Spring Boot", "MyBatis", "MySQL")));
		PostingAnalysis analysis = analysis(List.of("Java 기반 웹 개발", "SQL 작성"), List.of("Spring 프레임워크"),
				List.of("Java", "Spring"));

		Map<String, Requirement> requirements = requirements(analysis, cards);

		assertThat(requirements.get("Java 기반 웹 개발").cards()).containsExactly(1L, 2L);
		assertThat(requirements.get("SQL 작성").cards()).containsExactly(2L); // MySQL 을 썼으면 SQL 도
		assertThat(requirements.get("Spring 프레임워크").cards()).containsExactly(2L); // Spring Boot 는 Spring
		assertThat(CardMatching.match(analysis, cards, ProfileFacts.EMPTY, KD).matches().get(0).cardId()).isEqualTo(2);
	}

	@Test
	void implicationGoesOneWay() {
		Map<String, Requirement> requirements = requirements(analysis(List.of("Spring Boot 개발"), List.of(), List.of()),
				List.of(new MatchCard(1, "자바", "", List.of("Java"))));

		assertThat(requirements.get("Spring Boot 개발").cards()).isEmpty(); // Java 를 썼다고 Spring 을 쓴 건 아니다
	}

	@Test
	void rdbmsRequirementAcceptsOtherDatabases() {
		Map<String, Requirement> requirements = requirements(
				analysis(List.of("MSSQL 또는 Oracle 등 RDBMS 활용"), List.of(), List.of()),
				List.of(new MatchCard(1, "pg", "", List.of("PostgreSQL"))));

		assertThat(requirements.get("MSSQL 또는 Oracle 등 RDBMS 활용").cards()).containsExactly(1L);
	}

	@Test
	void noAnalysisNoMatches() {
		assertThat(CardMatching.match(null, CARDS, ProfileFacts.EMPTY, KD)).isEqualTo(Result.EMPTY);
	}

	@Test
	void eachRequirementGetsAResult() {
		PostingAnalysis analysis = analysis(List.of("Python 기반 API 개발", "Kubernetes 운영", "정처기 자격 보유"),
				List.of("TOEIC 800점 이상", "성실함"), List.of());

		Result result = CardMatching.match(analysis, CARDS, ProfileFacts.EMPTY, KD);

		assertThat(result.requirements()).extracting(RequirementResult::kind, RequirementResult::text,
				RequirementResult::status, RequirementResult::cards)
			.containsExactly(tuple("must", "Python 기반 API 개발", "matched", List.of(new RequirementCard(ETL, List.of("Python")))),
					tuple("must", "Kubernetes 운영", "unmatched", List.of()),
					// 자격증은 키워드 사전에 없어 카드로 판단할 수 없다. 프로필을 넣지 않았다고 없는 것으로 보지 않는다
					tuple("must", "정처기 자격 보유", "unknown", List.of()), tuple("nice", "TOEIC 800점 이상", "unknown", List.of()),
					tuple("nice", "성실함", "unknown", List.of()));
		assertThat(result.unmatched()).containsExactly("Kubernetes 운영");
	}

	@Test
	void eachRequirementCardGetsItsOverlapInTextOrder() {
		List<MatchCard> cards = List.of(new MatchCard(1, "알림 배치", "", List.of("배치")),
				new MatchCard(2, "주문 API 개선", "주문 서비스", List.of("스프링부트", "Docker", "Redis")),
				new MatchCard(3, "온보딩 문서", "", List.of("문서화")));
		PostingAnalysis analysis = analysis(List.of("배치 작업과 Spring 기반 Docker 운영, Docker 배포"), List.of("Redis 캐시"),
				List.of());

		Result result = CardMatching.match(analysis, cards, ProfileFacts.EMPTY, KD);

		// Docker 가 두 번 나와도 글에 나온 순(Spring, Docker)이다. Spring 은 스프링부트의 포함 관계로 겹친다. 요건마다 그 요건의 낱말만
		assertThat(result.requirements()).extracting(RequirementResult::cards)
			.containsExactly(List.of(new RequirementCard(1, List.of("배치")), new RequirementCard(2, List.of("Spring", "Docker"))),
					List.of(new RequirementCard(2, List.of("Redis"))));
		assertThat(result.matches()).extracting(CardMatch::cardId, CardMatch::experience, CardMatch::overlap)
			.containsExactly(tuple(2L, "주문 서비스", List.of("Spring", "Docker", "Redis")), tuple(1L, "", List.of("배치")));
	}

	@Test
	void profileAnswersRequirementsCardsCannot() {
		PostingAnalysis analysis = analysis(List.of("컴활 1급 이상", "컴퓨터공학 또는 관련 전공", "Kubernetes 운영"),
				List.of("TOEIC 800점 이상", "Python 기반 API 개발"), List.of());
		ProfileFacts profile = new ProfileFacts(List.of("컴퓨터활용능력"), List.of("TOEIC"), List.of("컴퓨터공학"));

		Result result = CardMatching.match(analysis, CARDS, profile, KD);

		assertThat(result.requirements()).extracting(RequirementResult::text, RequirementResult::status,
				RequirementResult::profileItems)
			.containsExactly(tuple("컴활 1급 이상", "profile", List.of("컴퓨터활용능력")), // 다른 표기도 자격증 사전으로 찾는다
					tuple("컴퓨터공학 또는 관련 전공", "profile", List.of("컴퓨터공학")),
					tuple("Kubernetes 운영", "unmatched", List.of()), tuple("TOEIC 800점 이상", "profile", List.of("TOEIC")),
					tuple("Python 기반 API 개발", "matched", List.of())); // 카드가 맞추면 카드가 먼저다
		assertThat(result.unmatched()).containsExactly("Kubernetes 운영");
	}

	@Test
	void profileDoesNotHideAGapCardsCanJudge() {
		PostingAnalysis analysis = analysis(List.of("정보처리기사 및 Kubernetes 운영 경험"), List.of(), List.of());
		ProfileFacts profile = new ProfileFacts(List.of("정보처리기사"), List.of(), List.of());

		Result result = CardMatching.match(analysis, CARDS, profile, KD);

		assertThat(result.requirements()).singleElement()
			.extracting(RequirementResult::status, RequirementResult::profileItems)
			.containsExactly("unmatched", List.of("정보처리기사"));
		assertThat(result.unmatched()).containsExactly("정보처리기사 및 Kubernetes 운영 경험");
	}

	@Test
	void profileValuesNeedWordBoundariesAndTwoLetters() {
		ProfileFacts profile = new ProfileFacts(List.of("C"), List.of("OPIc"), List.of("수"));

		assertThat(CardMatching.profileItems("OPIc IH 이상", profile)).containsExactly("OPIc");
		assertThat(CardMatching.profileItems("C 언어, OPIcs, 수 계산", profile)).isEmpty(); // 한 글자 값과 낱말 안의 글자는 보지 않는다
	}

	private static Map<String, Requirement> requirements(PostingAnalysis analysis, List<MatchCard> cards) {
		Map<Long, java.util.Set<String>> lowered = cards.stream()
			.collect(Collectors.toMap(MatchCard::id,
					(c) -> KD.expand(c.tags().stream().map(KD::normalize).toList())
						.stream()
						.map((t) -> t.toLowerCase(java.util.Locale.ROOT))
						.collect(Collectors.toSet()),
					(a, b) -> a, java.util.LinkedHashMap::new));
		List<String> allTags = cards.stream()
			.flatMap((c) -> KD.expand(c.tags().stream().map(KD::normalize).toList()).stream())
			.distinct()
			.toList();
		return CardMatching.requirements(analysis, lowered, allTags, KD)
			.stream()
			.collect(Collectors.toMap(Requirement::text, (r) -> r));
	}

	private static PostingAnalysis analysis(List<String> must, List<String> nice, List<String> keywords) {
		return new PostingAnalysis(must, nice, List.of(), keywords, List.of());
	}

}
