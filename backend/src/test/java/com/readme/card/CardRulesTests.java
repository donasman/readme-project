package com.readme.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.readme.card.CardRules.Content;

/**
 * 카드 규칙. jobfit 의 tests/test_experiences.py 에서 카드 검사·문항 유형·기간을 다루는 테스트를 옮겼고(위쪽), 아래는
 * experiences.py 와 exp_extract.py 의 ground_star 를 Python 3.12 로 직접 돌려 얻은 값이다(리드미 사전).
 *
 * <p>검사 글은 화면의 말로 바꿨으므로(CardRules 의 설명) 등급과 글 속의 값(61자, 대표 표기, 숫자 목록)을 견준다.
 * 옮기지 않은 것: id 겹침·예시 카드·verified 검사(리드미에 해당하지 않는다), CLI 와 YAML 파일 고치기 테스트.
 */
class CardRulesTests {

	private static final Content GOOD = new Content("주문 API 개선", "2025-03 ~ 2025-05", "N+1 제거와 캐시",
			List.of("Java", "Spring Boot"), List.of("직무역량"), new Star("s", "t", "a", "r"), List.of("p95 1.8초 → 0.4초"));

	// ── test_experiences.py ─────────────────────────────────────────────

	@Test
	void goodCardHasNoIssues() {
		assertThat(CardRules.check(GOOD)).isEmpty();
	}

	@Test
	void issuesAreReported() {
		// test_issues_are_reported_per_card 의 두 번째 카드. 태그는 저장할 때 대표 표기로 바꾸지만 검사는 받은 그대로 본다
		Content bad = new Content("주문 API 개선", "작년 봄", "가".repeat(61), List.of("스프링부트"), List.of("기술역량", "리더십"),
				GOOD.star(), List.of());
		assertThat(CardRules.check(bad)).extracting(CardIssue::level, CardIssue::field, CardIssue::message).containsExactly(
				tuple("경고", "요약", "요약이 61자입니다(60자 이내)"),
				tuple("경고", "태그", "태그 '스프링부트' 대신 대표 표기 'Spring Boot'(으)로 써 주세요"),
				tuple("경고", "쓸 문항 유형", "쓸 문항 유형의 '기술역량'은(는) '직무역량'(으)로 읽습니다"),
				tuple("오류", "쓸 문항 유형", "쓸 문항 유형의 '리더십'은(는) 지원동기 · 직무역량 · 협업 · 문제해결 · 성장 · 기타 중 하나여야 합니다"),
				tuple("경고", "근거 숫자", "근거 숫자가 없습니다 — 이 카드로 쓴 초안에는 수치를 쓸 수 없습니다"),
				tuple("경고", "기간", "기간 '작년 봄'을(를) 읽을 수 없습니다 (예: 2025-03 ~ 2025-06)"));
		// 세 번째 카드({"title": "제목만"})처럼 빈 칸은 오류다
		Content titleOnly = new Content("제목만", null, null, List.of(), List.of(), Star.EMPTY, List.of());
		assertThat(CardRules.check(titleOnly)).filteredOn((issue) -> issue.level().equals("오류"))
			.extracting(CardIssue::message)
			.containsExactly("태그 칸이 비어 있습니다", "요약 칸이 비어 있습니다", "쓸 문항 유형 칸이 비어 있습니다");
	}

	@Test
	void typesAreNormalized() {
		// test_types_and_tags_are_normalized 의 문항 유형 부분
		assertThat(List.of("기술역량", "팀워크").stream().map(CardRules::normalizeType)).containsExactly("직무역량", "협업");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "2025-03 ~ 2025-06", "2025.03", "2026 ~ 현재", "2024.09 – 2025.08",
			"2024-09 - 2025-08", "2025-07", "2026-08 ~ 현재" })
	void periodFormats(String period) {
		assertThat(periodIssues(period)).isEmpty();
	}

	@Test
	void unreadablePeriodIsAWarning() {
		assertThat(periodIssues("작년 봄")).hasSize(1);
	}

	// ── experiences.py 를 직접 돌려 얻은 값 ──────────────────────────────────

	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', nullValues = "null", value = {
			"\" 기술 역량 \" | 직무역량", "팀워크 | 협업", "직무 역량 | 직무역량", "기타 | 기타", "입사후 포부 | 지원동기",
			"\"성장\t과정\" | 성장", "리더십 | null", "\"\" | null", "Teamwork | null" })
	void normalizeTypeSameAsJobfit(String value, String expected) {
		assertThat(CardRules.normalizeType(value)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "\"{0}\" 읽음: {1}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
			// 월 한 자리, 끝을 비움, 긴 줄표, 사이의 공백, 전각 숫자
			"2025-3~2025-12 | true", "2025-03 ~ | true", "2025.03—2025.06 | true", "2025 ~현재 | true",
			"2025  ~  2026 | true", "2025.3 ~ 2025.12 | true", "２０２５.０３ | true",
			// 날짜까지 쓴 것, 붙여 쓴 -, 두 자리 연도, 한글 날짜
			"2025-03-01 | false", "2025-03-2025-06 | false", "25-03 | false", "2025년 3월 | false", "2025 -2026 | false",
			"2025-123 | false" })
	void periodSameAsJobfit(String period, boolean readable) {
		assertThat(periodIssues(period).isEmpty()).isEqualTo(readable);
	}

	@Test
	void summaryLengthCountsCharactersLikePython() {
		assertThat(summaryIssues("가".repeat(60))).isEmpty();
		assertThat(summaryIssues("가".repeat(61))).containsExactly("요약이 61자입니다(60자 이내)");
		// 이모지 하나는 한 글자다(Python 의 len)
		assertThat(summaryIssues("😀".repeat(61))).containsExactly("요약이 61자입니다(60자 이내)");
	}

	@Test
	void emptyStarPartsAndLooseNumbersAreWarnings() {
		assertThat(starIssues(new Star("", " ", "a", "r"))).containsExactly("STAR에서 비어 있는 칸: 상황, 과제");
		assertThat(starIssues(new Star("트래픽 2배", "t", "a", "30% 감소")))
			.containsExactly("STAR에 근거 숫자에 없는 숫자가 있습니다: 2배, 30% — 카드를 확인하면 근거 숫자에 넣습니다");
	}

	@Test
	void looseStarNumbersSameAsJobfit() {
		assertThat(CardRules.looseStarNumbers(new Star("", "", "", "p95 1.8초에서 0.4초로, 문의 30% 감소"), List.of("p95 1.8초 → 0.4초")))
			.containsExactly("30%");
		// 같은 표기는 한 번. 표기가 다르면(30 %) 따로 센다
		assertThat(CardRules.looseStarNumbers(new Star("주문 30% 증가", "", "", "문의 30% 감소, 30 % 감소"), List.of()))
			.containsExactly("30%", "30 %");
		// 버전, 날짜, [확인 필요] 는 숫자가 아니다
		assertThat(CardRules.looseStarNumbers(new Star("Java 17, Spring Boot 3로 [확인 필요: 40%]", "2025년 3월부터", "", ""),
				List.of())).isEmpty();
		// 같은 값의 다른 표기는 근거 숫자에 있는 것이다. 단위가 다르면 다른 값이다
		assertThat(CardRules.looseStarNumbers(new Star("", "", "4만 3천 건을 처리", "하루 43,000건"), List.of("4만 3천 건"))).isEmpty();
		assertThat(CardRules.looseStarNumbers(new Star("", "", "", "응답 1.8분"), List.of("1.8초"))).containsExactly("1.8분");
	}

	// ── 리드미에서 더한 것: 사전에 없는 태그 ─────────────────────────────────────

	@Test
	void unknownTagIsAWarning() {
		Content card = new Content("주문 API 개선", null, "요약", List.of("스프링부트", "Java", "없는태그"), List.of("직무역량"),
				GOOD.star(), GOOD.facts());
		assertThat(CardRules.check(card)).extracting(CardIssue::message).containsExactly(
				"태그 '스프링부트' 대신 대표 표기 'Spring Boot'(으)로 써 주세요", "태그 '없는태그'은(는) 키워드 사전에 없습니다");
	}

	// ── 카드 확인: ground_star(star, facts, None, vouched=True) 를 직접 돌려 얻은 값 ───────

	@Test
	void verifyMovesEachLooseNumberWithItsSentence() {
		// 문장마다 하나. 근거 숫자에 있는 숫자(0.4초는 없고 1.8초만 있다)는 옮기지 않는다
		assertThat(CardRules.factsToAdd(new Star("", "", "", "p95 응답 시간이 0.4초로 줄었다. 관련 문의도 30% 줄었다."), List.of("p95 1.8초")))
			.containsExactly("p95 응답 시간이 0.4초로 줄었다.", "관련 문의도 30% 줄었다.");
		// 한 번 옮긴 숫자는 다른 칸에서 다시 옮기지 않는다
		assertThat(CardRules.factsToAdd(new Star("하루 주문 2만 건. 피크에는 3배.", "", "", "2만 건을 1초 안에"), List.of()))
			.containsExactly("하루 주문 2만 건.", "피크에는 3배.", "2만 건을 1초 안에");
		// 목록 머리표는 떼고, 근거 숫자에 있는 것은 그대로 둔다
		assertThat(CardRules.factsToAdd(new Star("", "", "- 캐시 적중률 80% 달성\n- 배포 3회", ""), List.of("배포 3회")))
			.containsExactly("캐시 적중률 80% 달성");
		// [확인 필요] 와 버전은 옮길 숫자가 아니다
		assertThat(CardRules.factsToAdd(new Star("", "", "", "[확인 필요: 30%] 감소, Java 17 도입"), List.of())).isEmpty();
		assertThat(CardRules.factsToAdd(new Star("", "", "", "비용 50만 원 절감! 다음 달 40% 더."), List.of("비용 50만 원")))
			.containsExactly("다음 달 40% 더.");
	}

	@Test
	void verifyClipsLongSentencesAroundTheNumber() {
		String sentence = "지난 분기 주문 조회 화면이 느리다는 문의가 이어져 쿼리 로그를 하나씩 따라가 보니 주문마다 상품을 따로 읽는 구조였고 "
				+ "이를 fetch join 으로 묶어 주문 1건당 쿼리를 21회에서 1회로 줄였으며 화면 구성은 그대로 두었다";
		assertThat(CardRules.factsToAdd(new Star("", "", sentence, ""), List.of())).containsExactly(
				"…로그를 하나씩 따라가 보니 주문마다 상품을 따로 읽는 구조였고 이를 fetch join 으로 묶어 주문 1건당 쿼리를 21회에서 1회로 줄였으며 화면 구성은 그대로 두었다");
	}

	@Test
	void verifySkipsANumberWhoseSpellingIsLongerThanItsSentence() {
		// "5"와 단위 "명" 사이에 빈 줄이 400개라 숫자 표기가 문장(122자)보다 길다. jobfit 은 아무것도 넣지 않는다
		String result = "a".repeat(120) + " 5" + "\n".repeat(400) + "명";
		assertThat(CardRules.factsToAdd(new Star("", "", "", result), List.of())).isEmpty();
	}

	private static List<String> periodIssues(String period) {
		Content card = new Content(GOOD.title(), period, GOOD.summary(), GOOD.tags(), GOOD.usableFor(), GOOD.star(),
				GOOD.facts());
		return CardRules.check(card).stream().filter((issue) -> issue.field().equals("기간")).map(CardIssue::message).toList();
	}

	private static List<String> summaryIssues(String summary) {
		Content card = new Content(GOOD.title(), GOOD.period(), summary, GOOD.tags(), GOOD.usableFor(), GOOD.star(),
				GOOD.facts());
		return CardRules.check(card).stream().map(CardIssue::message).toList();
	}

	private static List<String> starIssues(Star star) {
		Content card = new Content(GOOD.title(), GOOD.period(), GOOD.summary(), GOOD.tags(), GOOD.usableFor(), star,
				GOOD.facts());
		return CardRules.check(card).stream().map(CardIssue::message).toList();
	}

}
