package com.readme.extraction;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.readme.common.error.ApiException;

/**
 * 올린 파일에서 글을 읽는다. 자료 올리기(CardSourceController)와 공고 읽어 오기(postingread)가 함께 쓴다.
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

	/** 자료 한 건으로 읽는다. 20MB 를 넘거나, 받지 않는 형식이거나, 읽을 글이 없으면 ApiException(ExtractionErrorCode). */
	SourceInput read(MultipartFile file) throws IOException {
		if (file.getSize() > SourceInput.MAX_BYTES) {
			throw new ApiException(ExtractionErrorCode.SOURCE_TOO_LARGE);
		}
		this.reading.acquireUninterruptibly();
		try {
			return SourceInput.file(file.getOriginalFilename(), file.getBytes());
		}
		finally {
			this.reading.release();
		}
	}

	/**
	 * PDF, DOCX, HWPX, HWP 파일의 다듬은 글(비지 않았고 100만 자까지). 다른 형식(PPTX, TXT, MD)은 SOURCE_UNSUPPORTED_TYPE 이고,
	 * 그 밖의 오류도 자료 올리기와 같은 ExtractionErrorCode 다.
	 */
	public String documentText(MultipartFile file) throws IOException {
		SourceInput input = read(file);
		if (!DOCUMENTS.contains(input.contentType())) {
			throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
		}
		return input.text();
	}

}
