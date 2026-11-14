package com.readme.extraction;

import static com.readme.extraction.HwpTextTests.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * PDF 의 글자 층. 시험 파일은 PDFBox 로 만든다(글꼴은 PDF 기본 글꼴이라 영문만 쓴다).
 * jobfit tests/test_exp_extract.py 의 test_read_any_formats 에서 PDF 부분을 옮겼다.
 */
class PdfTextTests {

	@Test
	void readsTheTextLayer() throws IOException {
		SourceFiles.Read read = SourceFiles.read("resume.pdf",
				pdf("Order API p95 1.8s to 0.4s, queries 21 to 1 per order with Redis cache"));

		assertThat(read.format()).isEqualTo(SourceFiles.Format.PDF);
		assertThat(read.text()).contains("21 to 1");
	}

	@Test
	void pagesAreSeparatedByABlankLineAndEmptyPagesAreSkipped() throws IOException {
		String first = "First page about the order API and its response time of 0.4 seconds";
		String second = "Second page about the payment batch moving thirty thousand alerts";

		assertThat(PdfText.read(pdf(first, "", second))).isEqualTo(first + "\n\n" + second);
	}

	@Test
	void aPdfWithoutATextLayerIsAScan() throws IOException {
		assertCode(() -> SourceFiles.read("scan.pdf", pdf("")), ExtractionErrorCode.SOURCE_NO_TEXT);
	}

	@Test
	void fewerThanFortyCharactersPerPageIsTreatedAsAScan() throws IOException {
		// 쪽 번호나 머리글만 글자로 남은 스캔본
		assertCode(() -> PdfText.read(pdf("Page 1", "Page 2")), ExtractionErrorCode.SOURCE_NO_TEXT);
	}

	@Test
	void aPdfThatNeedsAPasswordToOpenIsUnreadable() throws IOException {
		byte[] locked;
		try (PDDocument document = document("Order API p95 1.8s to 0.4s, queries 21 to 1 per order")) {
			document.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
			locked = save(document);
		}

		assertCode(() -> SourceFiles.read("locked.pdf", locked), ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	@Test
	void aPdfWithOnlyAnOwnerPasswordIsRead() throws IOException {
		byte[] restricted;
		try (PDDocument document = document("Order API p95 1.8s to 0.4s, queries 21 to 1 per order")) {
			AccessPermission permission = new AccessPermission();
			permission.setCanExtractContent(false);
			document.protect(new StandardProtectionPolicy("owner", "", permission));
			restricted = save(document);
		}

		assertThat(PdfText.read(restricted)).contains("21 to 1");
	}

	@Test
	void aContentStreamThatInflatesPastTheLimitIsRefusedBeforeTheTextIsRead() throws IOException {
		// 쪽의 내용 스트림에 풀면 MAX_INFLATED_BYTES 를 넘는 공백을 압축해 넣는다(압축하면 100KB 남짓)
		ByteArrayOutputStream deflated = new ByteArrayOutputStream();
		try (DeflaterOutputStream out = new DeflaterOutputStream(deflated)) {
			byte[] spaces = new byte[1 << 20];
			Arrays.fill(spaces, (byte) ' ');
			for (long written = 0; written <= SourceFiles.MAX_INFLATED_BYTES; written += spaces.length) {
				out.write(spaces);
			}
		}
		byte[] bomb;
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage();
			document.addPage(page);
			COSStream contents = document.getDocument().createCOSStream();
			try (OutputStream raw = contents.createRawOutputStream()) {
				raw.write(deflated.toByteArray());
			}
			contents.setItem(COSName.FILTER, COSName.FLATE_DECODE);
			page.setContents(new PDStream(contents));
			bomb = save(document);
		}

		assertThat(bomb.length).isLessThan((int) SourceInput.MAX_BYTES);
		assertCode(() -> SourceFiles.read("bomb.pdf", bomb), ExtractionErrorCode.SOURCE_TOO_LARGE);
	}

	@Test
	void aPageWithTooManyGlyphsIsStopped() throws IOException {
		// 글자마다 위치 정보가 메모리에 쌓이므로 한 쪽의 글자 수를 센다
		byte[] crowded = pdf("A".repeat(PdfText.MAX_GLYPHS_PER_PAGE + 1));

		assertCode(() -> PdfText.read(crowded), ExtractionErrorCode.SOURCE_TOO_LARGE);
	}

	/** 쪽마다 한 줄씩. 빈 글이면 글자가 없는 쪽이다. */
	static byte[] pdf(String... pages) throws IOException {
		try (PDDocument document = document(pages)) {
			return save(document);
		}
	}

	private static PDDocument document(String... pages) throws IOException {
		PDDocument document = new PDDocument();
		for (String text : pages) {
			PDPage page = new PDPage();
			document.addPage(page);
			if (text.isEmpty()) {
				continue;
			}
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
				content.newLineAtOffset(20, 700);
				content.showText(text);
				content.endText();
			}
		}
		return document;
	}

	private static byte[] save(PDDocument document) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		document.save(out);
		return out.toByteArray();
	}

}
