package com.readme.extraction;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiError;
import com.readme.common.error.ApiException;
import com.readme.extraction.CardSourceRequests.TextSourceRequest;
import com.readme.extraction.CardSourceRequests.UploadResponse;

/**
 * 자료 API(경험카드 설계 4.2). 카드 추출(POST …/extract, GET /api/card-extractions/{jobId})은 아직 없다.
 *
 * <p>올리기는 같은 주소가 파일(multipart, 칸 이름 file)과 붙여넣은 글(JSON { "text": … })을 받는다.
 * 새로 저장했으면 201, 이미 올린 자료(본문이 같다)면 200 과 duplicate: true 다.
 */
@RestController
class CardSourceController {

	/**
	 * 동시에 읽는 파일 수. 파일 하나를 읽는 데 메모리가 수십~수백 MB 든다(PDF·HWP 는 256MB 힙의 다른 프로세스).
	 * 기다리는 파일은 Tomcat 의 임시 파일에 있다.
	 */
	private static final int CONCURRENT_READS = 2;

	private final Semaphore reading = new Semaphore(CONCURRENT_READS);

	private final CardSourceQueries queries;

	private final CardSourceService service;

	CardSourceController(CardSourceQueries queries, CardSourceService service) {
		this.queries = queries;
		this.service = service;
	}

	/** 내 자료. 새 것이 앞이고, 자료마다 가장 최근 추출 작업을 싣는다. */
	@GetMapping("/api/card-sources")
	List<CardSourceResponse> mine(@CurrentUser LoginUser user) {
		return this.queries.findMine(user.id());
	}

	@PostMapping(path = "/api/card-sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file, @CurrentUser LoginUser user)
			throws IOException {
		if (file.getSize() > SourceInput.MAX_BYTES) {
			throw new ApiException(ExtractionErrorCode.SOURCE_TOO_LARGE);
		}
		SourceInput input;
		this.reading.acquireUninterruptibly();
		try {
			input = SourceInput.file(file.getOriginalFilename(), file.getBytes());
		}
		finally {
			this.reading.release();
		}
		return saved(input, user);
	}

	@PostMapping(path = "/api/card-sources", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<UploadResponse> uploadText(@Valid @RequestBody TextSourceRequest request,
			@CurrentUser LoginUser user) {
		return saved(SourceInput.text(request.text()), user);
	}

	/** 자료 본문을 지운다. 그 자료로 만든 카드는 남는다. 남의 자료는 404 SOURCE_NOT_FOUND. */
	@DeleteMapping("/api/card-sources/{sourceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long sourceId, @CurrentUser LoginUser user) {
		this.service.delete(user.id(), sourceId);
	}

	/**
	 * 20MB 를 넘는 파일. 실제 서버(Tomcat)는 multipart 의 max-file-size 에서 이 예외를 낸다.
	 * multipart 를 늦게 읽게 해 두어(spring.servlet.multipart.resolve-lazily) 이 컨트롤러의 인자를 만들 때 나므로 여기서 받는다.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> tooLarge() {
		ExtractionErrorCode code = ExtractionErrorCode.SOURCE_TOO_LARGE;
		return ResponseEntity.status(code.status()).body(ApiError.of(code));
	}

	private ResponseEntity<UploadResponse> saved(SourceInput input, LoginUser user) {
		CardSourceService.Saved saved = this.service.save(user.id(), input);
		CardSourceResponse source = this.queries.findMine(user.id(), saved.id()).orElseThrow();
		return ResponseEntity.status(saved.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
			.body(new UploadResponse(source, saved.duplicate()));
	}

}
