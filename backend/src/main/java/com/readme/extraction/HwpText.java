package com.readme.extraction;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DirectoryEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DocumentEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DocumentInputStream;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.Entry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.POIFSFileSystem;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;

import com.readme.common.error.ApiException;

/**
 * 옛 한글 파일(HWP 5)의 글을 읽는다(hwplib). 표 안의 글도 문단 사이에 넣는다. 이력서는 대개 표로 되어 있다.
 *
 * <p>hwplib 은 압축된 스트림을 상한 없이 메모리에 푼다. 그래서 넘기기 전에 모든 스트림을 같은 방법으로 풀어 보며 크기를 재고
 * (압축 폭탄), 암호·배포용 문서(배포용은 스트림이 암호화되어 미리 잴 수 없다)는 읽지 않는다.
 * 한글 파일인지는 FileHeader 스트림의 서명으로 본다(워드·엑셀의 옛 형식도 앞부분이 같다).
 *
 * <p>hwplib 1.1.11 의 글만 읽는 길(forExtractText)은 문단 번호 처리에서 NullPointerException 이 나서 문서 전체를 읽는 길을 쓴다.
 */
final class HwpText {

	private static final String SIGNATURE = "HWP Document File";

	private HwpText() {
	}

	static String read(byte[] bytes) {
		checkHeaderAndSize(bytes);
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

	/** 한글 파일인지, 암호·배포용이 아닌지, 풀면 너무 크지 않은지 본다. */
	private static void checkHeaderAndSize(byte[] bytes) {
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
			inflate(root, new SourceFiles.InflateBudget());
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw SourceFiles.unreadable(ex);
		}
	}

	/**
	 * 스트림을 hwplib 과 같은 방법(zlib 머리말 없는 deflate)으로 풀며 크기를 센다. 저장소(폴더)면 그 안의 스트림을 모두.
	 * 풀리지 않는 스트림은 압축하지 않은 것이다. hwplib 도 그런 스트림은 그대로 읽는다.
	 */
	private static void inflate(Entry entry, SourceFiles.InflateBudget budget) throws IOException {
		if (entry instanceof DirectoryEntry directory) {
			for (Entry child : directory) {
				inflate(child, budget);
			}
			return;
		}
		Inflater inflater = new Inflater(true);
		try (InputStream in = new InflaterInputStream(new DocumentInputStream((DocumentEntry) entry), inflater)) {
			budget.guard(in).transferTo(OutputStream.nullOutputStream());
		}
		catch (ZipException | EOFException notCompressed) {
			// 압축하지 않은 스트림
		}
		finally {
			inflater.end();
		}
	}

}
