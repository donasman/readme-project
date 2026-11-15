package com.readme.extraction;

import static com.readme.extraction.HwpTextTests.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 형식 알아내기(매직 바이트), 글 파일의 인코딩. 형식마다의 읽기는 PdfTextTests, HwpTextTests, ZipXmlTextTests 에 있다.
 */
class SourceFilesTests {

	@Test
	void textFilesAreDecodedFromUtf8() {
		SourceFiles.Read read = SourceFiles.read("경험.txt", "주문 API 개선\n- p95 1.8초".getBytes(StandardCharsets.UTF_8));

		assertThat(read).isEqualTo(new SourceFiles.Read(SourceFiles.Format.TXT, "주문 API 개선\n- p95 1.8초"));
		assertThat(SourceFiles.read("README.MD", "# 포트폴리오".getBytes(StandardCharsets.UTF_8)).format())
			.isEqualTo(SourceFiles.Format.MD);
	}

	@Test
	void textFilesWithABomOrInTheOldWindowsKoreanEncoding() {
		byte[] utf8Bom = concat(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }, "메모장".getBytes(StandardCharsets.UTF_8));
		byte[] utf16 = "메모장".getBytes(StandardCharsets.UTF_16); // 앞에 BOM 이 붙는다
		byte[] utf16le = concat(new byte[] { (byte) 0xFF, (byte) 0xFE }, "메모장".getBytes(StandardCharsets.UTF_16LE));
		byte[] cp949 = "메모장의 옛 기본값".getBytes(Charset.forName("x-windows-949"));

		assertThat(SourceFiles.read("a.txt", utf8Bom).text()).isEqualTo("메모장");
		assertThat(SourceFiles.read("a.txt", utf16).text()).isEqualTo("메모장");
		assertThat(SourceFiles.read("a.txt", utf16le).text()).isEqualTo("메모장");
		assertThat(SourceFiles.read("a.txt", cp949).text()).isEqualTo("메모장의 옛 기본값");
	}

	@Test
	void aTextFileWithNulBytesIsNotText() {
		assertCode(() -> SourceFiles.read("program.txt", new byte[] { 'M', 'Z', 0, 0, 1 }),
				ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
	}

	@Test
	void anUndecodableTextFileIsUnreadable() {
		assertCode(() -> SourceFiles.read("broken.txt", new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 'a' }),
				ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	@Test
	void photosHaveNoText() {
		assertCode(() -> SourceFiles.read("scan.png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10 }),
				ExtractionErrorCode.SOURCE_NO_TEXT);
		assertCode(() -> SourceFiles.read("scan.pdf", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 }),
				ExtractionErrorCode.SOURCE_NO_TEXT);
		assertCode(() -> SourceFiles.read("IMG_0001.HEIC",
				new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0 }),
				ExtractionErrorCode.SOURCE_NO_TEXT);
	}

	@Test
	void otherFormatsAreNotAccepted() {
		byte[] text = "이름,기간".getBytes(StandardCharsets.UTF_8);

		assertCode(() -> SourceFiles.read("경력.csv", text), ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
		assertCode(() -> SourceFiles.read("확장자없음", text), ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
		assertCode(() -> SourceFiles.read(null, text), ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
	}

	@Test
	void theContentDecidesNotTheExtension() throws Exception {
		assertThat(SourceFiles.read("이력서.txt", PdfTextTests.pdf("Order API p95 1.8s to 0.4s, queries 21 to 1 per order"))
			.format()).isEqualTo(SourceFiles.Format.PDF);
		assertThat(SourceFiles.read("이력서.pdf", ZipXmlTextTests.zip(Map.of("word/document.xml", "<w:p><w:t>글</w:t></w:p>")))
			.format()).isEqualTo(SourceFiles.Format.DOCX);
	}

	@Test
	void anEmptyFileHasNothingToRead() {
		assertCode(() -> SourceFiles.read("빈.pdf", new byte[0]), ExtractionErrorCode.SOURCE_EMPTY);
	}

	@Test
	void theServerDecidesTheContentType() {
		assertThat(SourceInput.file("경험.md", "# 경험".getBytes(StandardCharsets.UTF_8)).contentType())
			.isEqualTo("text/markdown");
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] joined = new byte[a.length + b.length];
		System.arraycopy(a, 0, joined, 0, a.length);
		System.arraycopy(b, 0, joined, a.length, b.length);
		return joined;
	}

}
