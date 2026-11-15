package com.readme.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 자료를 5,000자 조각으로 나누기. jobfit 의 ai/exp_extract.py {@code _split} 을 옮겼다.
 *
 * <p>jobfit 의 pytest 에는 {@code _split} 만 따로 본 것이 없고 test_long_source_is_split_and_run_prepares_prompts 가 조각이
 * 둘 이상 나오는지만 본다(첫 테스트). 나머지 값은 {@code _split} 을 Python 3.13 으로 직접 돌려 얻었다(python -I, 함수만 떼어 실행).
 * 조각 수와 조각마다의 글자 수(코드 포인트)다.
 */
class SourcePartsTests {

	@Test
	void longSourceIsSplitIntoPartsOfAtMostFiveThousand() {
		String paragraph = "프로젝트 경험 문단입니다. ".repeat(40);
		List<String> parts = SourceParts.split(String.join("\n\n", List.of(paragraph, paragraph, paragraph,
				paragraph, paragraph, paragraph, paragraph, paragraph, paragraph, paragraph, paragraph, paragraph)));

		assertThat(parts).hasSizeGreaterThanOrEqualTo(2).allSatisfy((part) -> assertThat(lengthOf(part)).isLessThanOrEqualTo(5000));
	}

	// ── jobfit 의 _split 을 직접 돌려 얻은 값 ─────────────────────────────

	@Test
	void shortTextIsOnePartStripped() {
		assertThat(SourceParts.split("  첫 문단\n\n둘째 문단  ")).containsExactly("첫 문단\n\n둘째 문단");
	}

	@Test
	void exactlyFiveThousandIsOnePartAndOneMoreIsTwo() {
		assertThat(lengths("가".repeat(5000))).containsExactly(5000);
		assertThat(lengths("가".repeat(5001))).containsExactly(5000, 1);
	}

	@Test
	void paragraphsAreKeptTogetherUntilTheNextOneDoesNotFit() {
		assertThat(lengths(String.join("\n\n", List.of("나".repeat(520), "나".repeat(520), "나".repeat(520),
				"나".repeat(520), "나".repeat(520), "나".repeat(520), "나".repeat(520), "나".repeat(520),
				"나".repeat(520), "나".repeat(520), "나".repeat(520), "나".repeat(520)))))
			.containsExactly(4688, 1562);
		assertThat(lengths(String.join("\n", List.of("자".repeat(999), "자".repeat(999), "자".repeat(999),
				"자".repeat(999), "자".repeat(999), "자".repeat(999)))))
			.containsExactly(4999, 999);
	}

	@Test
	void aParagraphLongerThanAPartIsCut() {
		List<String> parts = SourceParts.split("짧은 문단\n" + "다".repeat(12000) + "\n끝");

		assertThat(parts).extracting(SourcePartsTests::lengthOf).containsExactly(5, 5000, 5000, 2002);
		assertThat(parts.get(3)).endsWith("다\n끝");
	}

	@Test
	void cutPiecesKeepTheirSpacesButFlushedPartsAreStripped() {
		assertThat(lengths("타 ".repeat(2600) + "\n\n" + "파 ".repeat(2600))).containsExactly(5000, 199, 5000, 199);
	}

	@Test
	void theNewlineCountsTowardsTheBoundary() {
		assertThat(lengths("차".repeat(4999) + "\n카")).containsExactly(4999, 1);
		assertThat(lengths("차".repeat(4998) + "\n카")).containsExactly(5000);
	}

	@Test
	void unicodeSpacesMakeBlankLinesLikePython() {
		// Python 의 \s 와 strip() 은 전각 공백(U+3000)과 줄바꿈 없는 공백(U+00A0)도 공백으로 본다
		assertThat(SourceParts.split("라".repeat(3000) + "\n \t \n" + "마".repeat(3000) + "\n　\n" + "바".repeat(10)))
			.containsExactly("라".repeat(3000), "마".repeat(3000) + "\n" + "바".repeat(10));
		assertThat(lengths("사".repeat(4990) + "\n \n" + "아".repeat(20))).containsExactly(4990, 20);
	}

	@Test
	void lengthIsCountedInCodePointsLikePython() {
		List<String> parts = SourceParts.split("😀".repeat(5001));

		assertThat(parts).extracting(SourcePartsTests::lengthOf).containsExactly(5000, 1);
		assertThat(parts.get(1)).isEqualTo("😀");
	}

	@Test
	void countIsTheNumberOfParts() {
		assertThat(SourceParts.count("짧은 글")).isOne();
		assertThat(SourceParts.count("가".repeat(12001))).isEqualTo(3);
	}

	private static List<Integer> lengths(String text) {
		return SourceParts.split(text).stream().map(SourcePartsTests::lengthOf).toList();
	}

	private static int lengthOf(String text) {
		return text.codePointCount(0, text.length());
	}

}
