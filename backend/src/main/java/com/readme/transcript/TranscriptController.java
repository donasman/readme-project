package com.readme.transcript;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiError;
import com.readme.common.error.ApiException;
import com.readme.extraction.ExtractionErrorCode;
import com.readme.extraction.UploadedFiles;
import com.readme.llm.Llm;

/**
 * 성적증명서 읽기(학점 계산기 문서 4절). 로그인한 사람이 한 파일(multipart, 칸 이름 file)을 올린다. 저장하지 않는다(GC2).
 * 글은 자료 올리기와 같은 코드로 읽고(extraction 의 UploadedFiles), 한도는 읽기 전에 본다(GC9).
 */
@RestController
class TranscriptController {

	/** PDF 의 크기 상한(GC3). 사진의 5MB 는 UploadedFiles 가 본다 */
	static final long MAX_PDF_BYTES = 10L * 1024 * 1024;

	private final UploadedFiles files;

	private final TranscriptReader reader;

	private final Llm llm;

	TranscriptController(UploadedFiles files, TranscriptReader reader, Llm llm) {
		this.files = files;
		this.reader = reader;
		this.llm = llm;
	}

	@PostMapping(path = "/api/transcripts/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	TranscriptResponse read(@RequestParam("file") MultipartFile file, @CurrentUser LoginUser user) throws IOException {
		this.llm.requireBudget(user.id());
		String text;
		try {
			text = this.files.pdfOrImageText(file, MAX_PDF_BYTES, user.id());
		}
		catch (ApiException ex) {
			throw (ex.errorCode() instanceof ExtractionErrorCode code) ? new ApiException(fileError(code)) : ex;
		}
		return this.reader.read(text, user.id());
	}

	/** 20MB(multipart 의 max-file-size)를 넘는 파일. 공고 읽어 오기와 같은 까닭으로 여기서 받는다. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> tooLarge() {
		TranscriptErrorCode code = TranscriptErrorCode.TRANSCRIPT_TOO_LARGE;
		return ResponseEntity.status(code.status()).body(ApiError.of(code));
	}

	/** 자료 올리기의 오류를 이 기능의 코드로. 메시지는 이 코드의 것을 쓴다. */
	private static TranscriptErrorCode fileError(ExtractionErrorCode code) {
		return switch (code) {
			case SOURCE_TOO_LARGE -> TranscriptErrorCode.TRANSCRIPT_TOO_LARGE;
			case SOURCE_UNSUPPORTED_TYPE -> TranscriptErrorCode.TRANSCRIPT_UNSUPPORTED_TYPE;
			// 파일 읽기는 SOURCE_NOT_FOUND·JOB_NOT_FOUND 를 내지 않는다. 다 다루려고 둔다
			case SOURCE_UNREADABLE, SOURCE_NO_TEXT, SOURCE_EMPTY, SOURCE_NOT_FOUND, JOB_NOT_FOUND ->
				TranscriptErrorCode.TRANSCRIPT_UNREADABLE;
		};
	}

}
