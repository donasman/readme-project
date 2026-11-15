package com.readme.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.tool.blankfilemaker.BlankFileMaker;
import kr.dogfoot.hwplib.writer.HWPWriter;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.readme.common.error.ApiException;

/**
 * 옛 한글 파일(HWP). 시험 파일은 hwplib 으로 만든다. SourceFiles.read 를 부르는 것은 다른 프로세스(IsolatedReader)를 거친다.
 * 큰 파일이 메모리를 다 쓰는 경우는 IsolatedReaderTests 가 본다.
 */
class HwpTextTests {

	@Test
	void readsTheParagraphText() throws Exception {
		SourceFiles.Read read = SourceFiles.read("이력서.hwp", hwp("주문 조회 API 응답 시간 1.8초 → 0.4초"));

		assertThat(read.format()).isEqualTo(SourceFiles.Format.HWP);
		assertThat(SourceText.normalize(read.text())).isEqualTo("주문 조회 API 응답 시간 1.8초 → 0.4초");
	}

	@Test
	void theFormatComesFromTheContentNotTheExtension() throws Exception {
		assertThat(SourceFiles.read("resume.pdf", hwp("한글 파일")).format()).isEqualTo(SourceFiles.Format.HWP);
	}

	@Test
	void anOldWordFileLooksTheSameAtTheStartButIsNotHwp() throws Exception {
		byte[] notHwp = hwp("한글 파일");
		// FileHeader 의 서명을 지운다. 워드·엑셀의 옛 형식도 같은 컨테이너(OLE)다.
		notHwp[signatureAt(notHwp)] = 'X';

		assertCode(() -> SourceFiles.read("old.doc", notHwp), ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
	}

	@Test
	void passwordAndDistributionDocumentsAreNotRead() throws Exception {
		byte[] locked = hwp("암호가 걸린 파일");
		locked[signatureAt(locked) + 36] |= 0b010; // FileHeader 의 속성: 암호

		assertCode(() -> HwpText.read(locked), ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	@Test
	void aBrokenFileIsUnreadable() throws Exception {
		byte[] broken = Arrays.copyOf(hwp("한글 파일"), 600);

		assertCode(() -> SourceFiles.read("broken.hwp", broken), ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	private static int signatureAt(byte[] hwp) {
		byte[] signature = "HWP Document File".getBytes(StandardCharsets.US_ASCII);
		for (int i = 0; i + signature.length <= hwp.length; i++) {
			if (Arrays.equals(hwp, i, i + signature.length, signature, 0, signature.length)) {
				return i;
			}
		}
		throw new IllegalStateException("서명이 없다");
	}

	static void assertCode(ThrowingCallable call, ExtractionErrorCode code) {
		assertThatThrownBy(call).isInstanceOf(ApiException.class)
			.extracting((ex) -> ((ApiException) ex).errorCode()).isEqualTo(code);
	}

	/** 빈 한글 파일의 첫 문단에 글을 넣는다. */
	static byte[] hwp(String text) throws Exception {
		HWPFile file = BlankFileMaker.make();
		Paragraph paragraph = file.getBodyText().getSectionList().get(0).getParagraph(0);
		paragraph.getText().addString(text);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		HWPWriter.toStream(file, out);
		return out.toByteArray();
	}

}
