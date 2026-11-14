package com.readme.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import com.readme.common.error.ApiException;

/**
 * PDF 의 글자 층을 읽는다(PDFBox). jobfit 의 textio.py {@code _pdf} 와 같은 규칙이다: 쪽마다 글을 읽어 빈 쪽은 빼고 빈 줄로 잇고,
 * 글자가 쪽당 40자보다 적으면 스캔본으로 본다(SOURCE_NO_TEXT).
 *
 * <p>jobfit 과 다른 점: 암호가 걸린 PDF 라도 열람 암호 없이 열리면(인쇄·복사 제한만 건 PDF) 읽는다.
 *
 * <p>압축 폭탄: 쪽의 내용 스트림을 먼저 풀어 보며 크기를 재고(MAX_INFLATED_BYTES), 한 쪽의 글자가 너무 많으면 멈춘다.
 * PDFBox 는 글자마다 위치 정보를 메모리에 두기 때문이다.
 * shortcut: 글꼴 파일과 폼 XObject 안의 스트림은 미리 재지 않는다, 이것으로 서버가 멈추는 일이 생기면 읽기를 별도 프로세스나 시간 제한 안에서 돌린다.
 */
final class PdfText {

	/** 이보다 적으면 스캔한 PDF 로 본다(jobfit 의 PDF_MIN_CHARS_PER_PAGE) */
	static final int MIN_CHARS_PER_PAGE = 40;

	/** 한 쪽에서 읽는 글자 수의 상한. 보통의 쪽은 수천 자다 */
	static final int MAX_GLYPHS_PER_PAGE = 200_000;

	private PdfText() {
	}

	static String read(byte[] bytes) {
		// 풀어낸 스트림을 메모리에 둘 때의 상한. 넘으면 PDFBox 가 IOException 을 낸다.
		try (PDDocument document = Loader.loadPDF(bytes, "", null, null,
				MemoryUsageSetting.setupMainMemoryOnly(SourceFiles.MAX_INFLATED_BYTES).streamCache)) {
			checkContentSize(document);
			int pages = document.getNumberOfPages();
			SourceText.Collector text = new SourceText.Collector();
			PDFTextStripper stripper = new LimitedStripper();
			boolean started = false;
			for (int page = 1; page <= pages; page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				String pageText = stripper.getText(document).strip();
				if (!pageText.isEmpty()) {
					text.add(started ? "\n\n" : "").add(pageText);
					started = true;
				}
			}
			String result = text.toString();
			if (SourceText.length(result) < MIN_CHARS_PER_PAGE * Math.max(pages, 1)) {
				throw new ApiException(ExtractionErrorCode.SOURCE_NO_TEXT);
			}
			return result;
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (IOException | RuntimeException ex) {
			throw SourceFiles.unreadable(ex);
		}
	}

	/** 모든 쪽의 내용 스트림을 풀어 보며 크기를 잰다. 넘으면 SOURCE_TOO_LARGE. */
	private static void checkContentSize(PDDocument document) throws IOException {
		SourceFiles.InflateBudget budget = new SourceFiles.InflateBudget();
		for (PDPage page : document.getPages()) {
			try (InputStream contents = page.getContents()) {
				budget.guard(contents).transferTo(OutputStream.nullOutputStream());
			}
		}
	}

	/** 한 쪽의 글자가 MAX_GLYPHS_PER_PAGE 를 넘으면 멈춘다. */
	private static final class LimitedStripper extends PDFTextStripper {

		private int glyphs;

		@Override
		protected void startPage(PDPage page) throws IOException {
			this.glyphs = 0;
			super.startPage(page);
		}

		@Override
		protected void processTextPosition(TextPosition text) {
			if (++this.glyphs > MAX_GLYPHS_PER_PAGE) {
				throw SourceText.tooLong();
			}
			super.processTextPosition(text);
		}

	}

}
