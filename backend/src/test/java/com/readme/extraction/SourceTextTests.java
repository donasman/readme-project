package com.readme.extraction;

import static com.readme.extraction.HwpTextTests.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;

import org.junit.jupiter.api.Test;

/**
 * 읽어 낸 글을 다듬는 규칙과 파일 이름 다듬기.
 */
class SourceTextTests {

	@Test
	void newlinesBecomeLineFeeds() {
		assertThat(SourceText.normalize("첫 줄\r\n둘째 줄\r셋째 줄\n넷째 줄")).isEqualTo("첫 줄\n둘째 줄\n셋째 줄\n넷째 줄");
	}

	@Test
	void controlCharactersNulBomAndLoneSurrogatesAreDroppedButTabsStay() {
		// PostgreSQL 의 text 는 NUL 을 받지 않는다
		assertThat(SourceText.normalize("\uFEFF이름\u0000\t홍길동\u0007\u001B\u0085\u007F 경력\uD800"))
			.isEqualTo("이름\t홍길동 경력");
		assertThat(SourceText.normalize("이모지는 남는다 😀")).isEqualTo("이모지는 남는다 😀");
	}

	@Test
	void decomposedHangulIsComposed() {
		String decomposed = Normalizer.normalize("한글 이력서", Normalizer.Form.NFD);

		assertThat(SourceText.normalize(decomposed)).isEqualTo("한글 이력서");
		assertThat(SourceText.length(SourceText.normalize(decomposed))).isEqualTo(6);
	}

	@Test
	void surroundingBlankLinesAreTrimmed() {
		assertThat(SourceText.normalize("\n\n  본문  \n\n")).isEqualTo("본문");
	}

	@Test
	void emptyAndTooLongTextIsRefused() {
		assertCode(() -> SourceText.check(SourceText.normalize(" \n\u0000\t ")), ExtractionErrorCode.SOURCE_EMPTY);
		assertCode(() -> SourceText.check("가".repeat(SourceText.MAX_CHARS + 1)), ExtractionErrorCode.SOURCE_TOO_LARGE);
		SourceText.check("가".repeat(SourceText.MAX_CHARS));
	}

	@Test
	void pastedTextFollowsTheSameRules() {
		SourceInput input = SourceInput.text("\r\n  경험 정리\u0000\r\n");

		assertThat(input.kind()).isEqualTo(SourceInput.Kind.TEXT);
		assertThat(input.text()).isEqualTo("경험 정리");
		assertThat(input.fileName()).isNull();
		assertThat(input.contentType()).isNull();
		assertThat(input.byteSize()).isNull();
		assertCode(() -> SourceInput.text("   "), ExtractionErrorCode.SOURCE_EMPTY);
		// 다듬기 전에 READ_LIMIT 를 넘으면 사본을 만들지 않고 거부한다
		assertCode(() -> SourceInput.text(" ".repeat(SourceText.READ_LIMIT + 1)), ExtractionErrorCode.SOURCE_TOO_LARGE);
	}

	// ── 파일 이름 ─────────────────────────────────────────────────────

	@Test
	void fileNamesLoseTheirPathAndControlCharacters() {
		assertThat(SourceInput.cleanName("C:\\Users\\다온\\이력서.pdf")).isEqualTo("이력서.pdf");
		assertThat(SourceInput.cleanName("../../etc/passwd")).isEqualTo("passwd");
		assertThat(SourceInput.cleanName("이력서\r\n\u0000.pdf")).isEqualTo("이력서.pdf");
		assertThat(SourceInput.cleanName(Normalizer.normalize("포트폴리오.pdf", Normalizer.Form.NFD))).isEqualTo("포트폴리오.pdf");
	}

	@Test
	void blankFileNamesBecomeNullAndLongOnesAreCut() {
		assertThat(SourceInput.cleanName(null)).isNull();
		assertThat(SourceInput.cleanName("  ")).isNull();
		assertThat(SourceInput.cleanName("folder/")).isNull();
		assertThat(SourceInput.cleanName("가".repeat(300) + ".pdf")).isEqualTo("가".repeat(255));
		assertThat(SourceInput.cleanName("😀".repeat(300))).isEqualTo("😀".repeat(255));
	}

}
