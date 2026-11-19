package com.readme.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.readme.common.error.ApiException;
import com.readme.llm.LlmDocument;

/**
 * 올린 파일에서 글을 읽는다. 자료 올리기(CardSourceController), 공고 읽어 오기(postingread), 성적증명서 읽기(transcript)가 함께 쓴다.
 *
 * <p>동시에 읽는 파일 수는 둘을 합쳐 CONCURRENT_READS 개다. 파일 하나를 읽는 데 메모리가 수십~수백 MB 든다
 * (PDF·HWP 는 256MB 힙의 다른 프로세스). 기다리는 파일은 Tomcat 의 임시 파일에 있다.
 */
@Component
public class UploadedFiles {

	private static final int CONCURRENT_READS = 2;

	/** 공고 읽어 오기가 받는 형식(화면·API 대응 문서 3절 7번). 서버가 내용으로 알아낸 형식이다 */
	private static final Set<String> DOCUMENTS = Set.of(SourceFiles.Format.PDF.contentType,
			SourceFiles.Format.DOCX.contentType, SourceFiles.Format.HWPX.contentType, SourceFiles.Format.HWP.contentType);

	private final Semaphore reading = new Semaphore(CONCURRENT_READS);

	private final ScannedFiles scanned;

	UploadedFiles(ScannedFiles scanned) {
		this.scanned = scanned;
	}

	/**
	 * 자료 한 건으로 읽는다. 20MB 를 넘거나, 받지 않는 형식이거나, 읽을 글이 없으면 ApiException(ExtractionErrorCode).
	 * 글자 층이 없는 PDF(스캔본)와 사진은 Claude 로 옮겨 적는다(ScannedFiles). 그때 LLM 한도와 사용액은 userId 의 것이다.
	 */
	SourceInput read(MultipartFile file, long userId) throws IOException {
		if (file.getSize() > SourceInput.MAX_BYTES) {
			throw new ApiException(ExtractionErrorCode.SOURCE_TOO_LARGE);
		}
		byte[] bytes;
		Integer pages;
		this.reading.acquireUninterruptibly();
		try {
			bytes = file.getBytes();
			try {
				return SourceInput.file(file.getOriginalFilename(), bytes);
			}
			catch (ApiException ex) {
				if (ex.errorCode() != ExtractionErrorCode.SOURCE_NO_TEXT) {
					throw ex;
				}
				pages = (Integer) ex.details().get(PdfText.PAGES);
			}
		}
		finally {
			this.reading.release();
		}
		// 옮겨 적는 동안(수십 초)은 읽는 수를 잡지 않는다. 그 수는 서버 메모리를 지키려는 것이다
		String text = this.scanned.read(userId, bytes, pages);
		return SourceInput.scanned(file.getOriginalFilename(), SourceFiles.llmMediaType(bytes), bytes, text);
	}

	/**
	 * 공고 파일의 다듬은 글(비지 않았고 100만 자까지). PDF, DOCX, HWPX, HWP 와, 글자 층이 없어 Claude 로 옮겨 적은 PDF·사진이다.
	 * 다른 형식(PPTX, TXT, MD)은 SOURCE_UNSUPPORTED_TYPE 이고, 그 밖의 오류도 자료 올리기와 같은 ExtractionErrorCode 다.
	 * 옮겨 적을 때의 LLM 오류(한도, 키 없음)는 LlmErrorCode 그대로다.
	 */
	public String documentText(MultipartFile file, long userId) throws IOException {
		SourceInput input = read(file, userId);
		if (!DOCUMENTS.contains(input.contentType()) && !input.contentType().startsWith("image/")) {
			throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
		}
		return input.text();
	}

	/**
	 * 성적증명서(학점 계산기 GC3)의 다듬은 글. PDF 와 Claude 가 받는 사진(JPG, PNG, GIF, WebP)만 받는다(SOURCE_UNSUPPORTED_TYPE).
	 * PDF 는 maxPdfBytes, 사진은 5MB 까지다(SOURCE_TOO_LARGE). 형식은 파일을 다 읽기 전에 앞부분으로 본다. 그 밖은 documentText 와 같다.
	 */
	public String pdfOrImageText(MultipartFile file, long maxPdfBytes, long userId) throws IOException {
		byte[] head;
		try (InputStream in = file.getInputStream()) {
			head = in.readNBytes(1024);
		}
		String mediaType = SourceFiles.llmMediaType(head);
		if (mediaType == null) {
			throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
		}
		if (file.getSize() > (LlmDocument.PDF.equals(mediaType) ? maxPdfBytes : ScannedFiles.MAX_IMAGE_BYTES)) {
			throw new ApiException(ExtractionErrorCode.SOURCE_TOO_LARGE);
		}
		return read(file, userId).text();
	}

}
