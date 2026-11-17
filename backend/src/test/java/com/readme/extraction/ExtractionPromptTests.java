package com.readme.extraction;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** jobfit tests/test_exp_extract.py 의 test_fit_existing_prefers_full_then_titles, test_prompt_stays_under_limit_with_many_cards. */
class ExtractionPromptTests {

	@Test
	void existingCardsFitTheBudget() {
		assertThat(ExtractionPrompt.fitExisting(List.of("주문 API", "배치 개선"), 1000)).isEqualTo("- 주문 API\n- 배치 개선");
		assertThat(ExtractionPrompt.fitExisting(List.of(), 100)).isEqualTo("(없음)");
		List<String> many = IntStream.range(0, 120).mapToObj((n) -> "아주 긴 제목의 프로젝트 경험 카드 번호 " + n).toList();
		String fitted = ExtractionPrompt.fitExisting(many, 2000);
		assertThat(fitted.length()).isLessThanOrEqualTo(2000);
		assertThat(fitted).endsWith("개 (제목이 같은 카드는 저장할 때 건너뜁니다)").contains("- … 외 ");
	}

	@Test
	void listFitsTheBudgetWithTheRestCounted() {
		assertThat(ExtractionPrompt.fitList(List.of(), 10)).isEqualTo("(없음)");
		assertThat(ExtractionPrompt.fitList(List.of("Java", "Spring"), 100)).isEqualTo("Java, Spring");
		assertThat(ExtractionPrompt.fitList(List.of("Java", "Spring", "Kubernetes"), 18)).isEqualTo("Java, 외 2개");
	}

	@Test
	void questionHasExistingCardsTagsAndThePart() {
		String question = ExtractionPrompt.question(List.of("주문 API"), List.of("Java", "백엔드", "Java"), "이력서.pdf", 2, 3,
				"본문");

		assertThat(question).isEqualTo("## 이미 있는 카드\n- 주문 API\n\n## 태그 표기 (참고)\nJava\n\n## 자료 — 이력서.pdf (조각 2/3)\n본문");
		assertThat(ExtractionPrompt.question(List.of(), List.of(), null, 1, 1, "본문")).endsWith("## 자료 — 붙여넣은 글\n본문");
	}

	@Test
	void titlesAreComparedWithoutSpacesSymbolsAndCase() {
		assertThat(ExtractionPrompt.normalizeTitle(" 주문 조회 API—개선 (프로젝트) ")).isEqualTo("주문조회api개선프로젝트");
		assertThat(ExtractionPrompt.normalizeTitle("ＡＰＩ")).isEqualTo("api");
	}

	@Test
	void systemPromptIsTheFileWithoutPlaceholders() {
		assertThat(ExtractionPrompt.SYSTEM).contains("## cards 규칙", "## profile 규칙").doesNotContain("{{");
	}

}
