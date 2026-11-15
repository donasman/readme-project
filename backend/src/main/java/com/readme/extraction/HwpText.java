package com.readme.extraction;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DirectoryEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DocumentEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DocumentInputStream;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.POIFSFileSystem;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;

import com.readme.common.error.ApiException;

/**
 * 옛 한글 파일(HWP 5)의 글을 읽는다(hwplib). 표 안의 글도 문단 사이에 넣는다. 이력서는 대개 표로 되어 있다.
 *
 * <p>한글 파일인지는 FileHeader 스트림의 서명으로 본다(워드·엑셀의 옛 형식도 앞부분이 같다). 암호·배포용 문서는 읽지 않는다.
 * hwplib 은 압축된 스트림을 상한 없이 풀고 글자마다 객체를 만든다. 그래서 서버는 이것을 다른 프로세스에서 부른다(IsolatedReader).
 *
 * <p>hwplib 1.1.11 의 글만 읽는 길(forExtractText)은 문단 번호 처리에서 NullPointerException 이 나서 문서 전체를 읽는 길을 쓴다.
 */
final class HwpText {

	private static final String SIGNATURE = "HWP Document File";

	private HwpText() {
	}

	static String read(byte[] bytes) {
		checkHeader(bytes);
		try {
			String text = TextExtractor.extract(HWPReader.fromInputStream(new ByteArrayInputStream(bytes)),
					TextExtractMethod.InsertControlTextBetweenParagraphText);
			if (text.length() > SourceText.READ_LIMIT) {
				throw SourceText.tooLong();
			}
			return text;
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw SourceFiles.unreadable(ex);
		}
	}

	/** 한글 파일인지, 암호·배포용 문서가 아닌지 본다. */
	private static void checkHeader(byte[] bytes) {
		try {
			// hwplib 이 품은 옛 POI 다. 메모리에서만 읽어 닫을 것이 없다.
			DirectoryEntry root = new POIFSFileSystem(new ByteArrayInputStream(bytes)).getRoot();
			if (!root.hasEntry("FileHeader") || !(root.getEntry("FileHeader") instanceof DocumentEntry header)) {
				throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
			}
			byte[] head = new byte[40];
			try (DocumentInputStream in = new DocumentInputStream(header)) {
				in.readFully(head);
			}
			if (!new String(head, 0, SIGNATURE.length(), StandardCharsets.US_ASCII).equals(SIGNATURE)) {
				throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
			}
			int properties = (head[36] & 0xFF) | (head[37] & 0xFF) << 8;
			if ((properties & 0b110) != 0) { // 암호, 배포용 문서
				throw new ApiException(ExtractionErrorCode.SOURCE_UNREADABLE);
			}
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw SourceFiles.unreadable(ex);
		}
	}

}
