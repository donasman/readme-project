package com.readme.extraction;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

import com.readme.common.error.ApiException;

/**
 * PDF 의 글자 층을 읽는다(PDFBox). jobfit 의 textio.py {@code _pdf} 와 같은 규칙이다: 쪽마다 글을 읽어 빈 쪽은 빼고 빈 줄로 잇고,
 * 글자가 쪽당 40자보다 적으면 스캔본으로 본다(SOURCE_NO_TEXT).
 *
 * <p>jobfit 과 다른 점: 암호가 걸린 PDF 라도 열람 암호 없이 열리면(인쇄·복사 제한만 건 PDF) 읽는다.
 *
 * <p>서버는 이것을 다른 프로세스에서 부른다(IsolatedReader). 작은 PDF 도 풀면 메모리를 다 쓸 수 있어서다.
 */
final class PdfText {

	/** 이보다 적으면 스캔한 PDF 로 본다(jobfit 의 PDF_MIN_CHARS_PER_PAGE) */
	static final int MIN_CHARS_PER_PAGE = 40;

	/** SOURCE_NO_TEXT 의 details 에 싣는 쪽 수의 이름 */
	static final String PAGES = "pages";

	/** 쪽의 끝 표시. 문서를 한 번에 읽고 이것으로 쪽을 나눈다 */
	private static final String PAGE_END = "\f";

	private PdfText() {
	}

	static String read(byte[] bytes) {
		try (PDDocument document = Loader.loadPDF(bytes)) {
			// 쪽마다 getText 를 부르면 부를 때마다 문서 전체를 돈다. 한 번에 읽고 쪽 끝 표시로 나눈다.
			// 쪽 수도 /Count 가 아니라 실제로 읽은 쪽을 센다.
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setPageEnd(PAGE_END);
			SourceText.Collector collector = new SourceText.Collector();
			stripper.writeText(document, collector);
			String[] pages = collector.toString().split(PAGE_END, -1);
			int pageCount = Math.max(pages.length - 1, 1);
			String text = Arrays.stream(pages).map(String::strip).filter((page) -> !page.isEmpty())
				.collect(Collectors.joining("\n\n"));
			if (SourceText.length(text) < MIN_CHARS_PER_PAGE * pageCount) {
				// 쪽 수는 스캔본을 Claude 로 읽을지 정하는 데 쓴다(ScannedFiles). IsolatedReader 가 서버 쪽에 넘긴다.
				// 글이 없는 쪽은 쪽 끝 표시가 나오지 않고 /Count 는 믿을 수 없어, 쪽 나무를 하나씩 센다
				int walked = 0;
				for (PDPage ignored : document.getPages()) {
					walked++;
				}
				throw new ApiException(ExtractionErrorCode.SOURCE_NO_TEXT).with(PAGES, Math.max(walked, pageCount));
			}
			return text;
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (IOException | RuntimeException ex) {
			throw SourceFiles.unreadable(ex);
		}
	}

}
