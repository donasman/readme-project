package com.readme.card;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.readme.card.CardRules.Grounding;
import com.readme.card.CardRules.Normalized;
import com.readme.card.CardRules.SourceText;
import com.readme.extraction.ExtractedCard;
import com.readme.extraction.ExtractedCard.StarText;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추출한 카드 다듬기와 STAR 숫자 맞추기. jobfit tests/test_exp_extract.py 의 test_extract_flow(카드 다듬기 부분),
 * test_star_numbers_must_be_in_facts, test_ground_star_edges(원문 대조 길)를 옮겼다. 안내 글은 화면의 말("근거 숫자")이다.
 */
class CardExtractionRulesTests {

	/** jobfit 테스트의 RESUME(연락처 줄은 자료 올리기가 가린다) */
	private static final String RESUME = """
			홍길동 · [전화번호] · hong@example.com

			주문 조회 API 개선 프로젝트 (2025-03 ~ 2025-05)
			- N+1 조회를 fetch join으로 묶고 Redis 캐시를 붙였다
			- p95 응답 시간 1.8초 → 0.4초
			- 주문 1건당 쿼리 21회 → 1회

			결제 알림 배치 개선 (2024-09 ~ 2024-12)
			- 하루 3만 건 알림을 Spring Batch로 옮김
			""";

	@Test
	void numbersNotInTheSourceAreDroppedAndTypesAndTagsAreNormalized() {
		ExtractedCard raw = new ExtractedCard("결제 알림 배치 개선", "2024-09 ~ 2024-12", "", "", List.of("스프링 배치", "배치"),
				"알림 배치를 Spring Batch로 옮김", new StarText("알림이 늦었다", "", "Spring Batch로 옮김", ""),
				List.of("하루 3만 건 알림", "처리 시간 70% 단축"), List.of("문제해결", "리더십"));

		Normalized card = CardRules.normalizeExtracted(raw, SourceText.of(RESUME));

		assertThat(card.card().facts()).containsExactly("하루 3만 건 알림");
		assertThat(card.card().tags()).containsExactly("Spring Batch", "배치");
		assertThat(card.card().usableFor()).containsExactly("문제해결");
		assertThat(card.card().star()).isEqualTo(new Star("알림이 늦었다", "", "Spring Batch로 옮김", ""));
		assertThat(card.card().org()).isNull();
		assertThat(card.notes()).containsExactly("문항 유형이 아니라서 뺐습니다 — 리더십",
				"자료 원문에 없는 숫자가 있어 이 근거 숫자를 뺐습니다 — \"처리 시간 70% 단축\"");
	}

	@Test
	void starNumbersAreGroundedInTheSource() {
		ExtractedCard raw = new ExtractedCard("결제 알림 배치 개선", "", "", "", List.of(), "",
				new StarText("", "", "하루 3만 건 알림을 옮김", "처리 시간 70% 단축"), List.of(), List.of());

		Normalized card = CardRules.normalizeExtracted(raw, SourceText.of(RESUME));

		assertThat(card.card().facts()).containsExactly("하루 3만 건 알림을 Spring Batch로 옮김"); // 원문 문장 그대로
		assertThat(card.card().star().result()).isEqualTo("처리 시간 [확인 필요: 70%] 단축");
		assertThat(card.notes()).containsExactly(
				"STAR에만 있던 숫자(3만 건)를 근거 숫자에 넣었습니다 — \"하루 3만 건 알림을 Spring Batch로 옮김\"",
				"STAR의 숫자(70%)는 자료 원문에 없어 [확인 필요: 70%]로 바꿨습니다");
	}

	@Test
	void longSourceSentenceIsClippedAroundTheNumber() {
		String longLine = "회사 소개와 프로젝트 배경 설명이 길게 이어지는 문단입니다 ".repeat(3)
				+ "그 결과 월 처리량 12만 건을 안정적으로 소화했고 " + "이후 운영 이야기가 계속 이어집니다 ".repeat(3);

		Grounding grounding = CardRules.groundStar(new Star("", "", "", "월 12만 건 처리"), List.of(), SourceText.of(longLine));

		assertThat(grounding.factsToAdd()).hasSize(1);
		String fact = grounding.factsToAdd().get(0);
		assertThat(fact.length()).isLessThanOrEqualTo(120 + 2);
		assertThat(fact).contains("12만 건").startsWith("…").endsWith("…");
	}

	@Test
	void onlyNumbersMissingFromTheSourceAreMarkedNotPartsOfLongerOnes() {
		Grounding grounding = CardRules.groundStar(new Star("", "", "2023년에 30% 늘리고 3% 줄임", ""), List.of(),
				SourceText.of("2023년 30% 늘림"));

		assertThat(grounding.star().action()).isEqualTo("2023년에 30% 늘리고 [확인 필요: 3%] 줄임");
	}

	@Test
	void numbersAlreadyMarkedStayAsTheyAre() {
		assertThat(CardRules.markUnconfirmed("[확인 필요: 3%] 3% 13% 3.5%", "3%")).isEqualTo("[확인 필요: 3%] [확인 필요: 3%] 13% 3.5%");
		assertThat(CardRules.markUnconfirmed("없음", "3%")).isEqualTo("없음");
	}

	@Test
	void cardWithoutATitleIsDropped() {
		assertThat(CardRules.normalizeExtracted(new ExtractedCard(" ", "", "", "", List.of(), "", null, List.of(), List.of()),
				SourceText.of(RESUME)))
			.isNull();
	}

	@Test
	void tooLongValuesAreCutToTheColumns() {
		ExtractedCard raw = new ExtractedCard("가".repeat(250), "2024 ~ ".repeat(20), null, null, null, "요".repeat(300),
				null, null, null);

		Normalized card = CardRules.normalizeExtracted(raw, SourceText.of(""));

		assertThat(card.card().title()).hasSize(200);
		assertThat(card.card().period().length()).isLessThanOrEqualTo(50);
		assertThat(card.card().summary()).hasSize(200);
		assertThat(card.card().star()).isEqualTo(Star.EMPTY);
		assertThat(card.card().tags()).isEmpty();
	}

}
