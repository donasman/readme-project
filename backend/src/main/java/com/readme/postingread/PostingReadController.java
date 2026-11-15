package com.readme.postingread;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiError;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.extraction.ExtractionErrorCode;
import com.readme.extraction.UploadedFiles;
import com.readme.group.GroupAccess;
import com.readme.postingread.PostingReadResponse.Duplicate;

/**
 * 공고 읽어 오기(공고 설계 4.6). 로그인한 사람이면 부를 수 있고, groupId 를 넣으면 그 그룹의 멤버만이다.
 * 같은 주소가 JSON(링크나 붙여넣은 본문)과 파일(multipart, 칸 이름 file)을 받는다. 파일의 글은 붙여넣은 본문과 같은 길로 읽는다.
 * 읽은 결과는 저장하지 않는다. 읽지 못하면 FETCH_…·FILE_… 오류의 메시지로 본문 붙여넣기를 안내한다.
 * 규칙으로 못 읽은 필수 칸은 LLM 으로 채운다(PostingFieldsByLlm, 읽는 사람의 사용액).
 */
@RestController
class PostingReadController {

	private final GroupAccess access;

	private final PostingReadQueries queries;

	private final PostingReader reader;

	private final UploadedFiles files;

	private final PostingFieldsByLlm llmFields;

	private final Clock clock;

	PostingReadController(GroupAccess access, PostingReadQueries queries, PostingReader reader, UploadedFiles files,
			PostingFieldsByLlm llmFields, Clock clock) {
		this.access = access;
		this.queries = queries;
		this.reader = reader;
		this.files = files;
		this.llmFields = llmFields;
		this.clock = clock;
	}

	@PostMapping("/api/postings/read")
	PostingReadResponse read(@Valid @RequestBody PostingReadRequest request, @CurrentUser LoginUser user) {
		boolean byUrl = request.url() != null;
		if (byUrl == request.hasText()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "링크(url)와 본문(text) 가운데 하나만 넣어 주세요");
		}
		if (byUrl && request.hasSourceUrl()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "원문 링크(sourceUrl)는 본문(text)과 함께만 넣어 주세요");
		}
		String link = byUrl ? request.url().strip() : (request.hasSourceUrl() ? request.sourceUrl().strip() : null);
		Duplicate duplicate = null;
		if (request.groupId() != null) {
			this.access.requireMember(request.groupId(), user.id());
			duplicate = this.queries.sameInGroup(request.groupId(), link).orElse(null);
		}
		LocalDate today = LocalDate.now(this.clock);
		if (!byUrl) {
			return this.llmFields.fill(this.reader.fromText(request.text(), link, today), user.id(), today)
				.with(duplicate);
		}
		if (duplicate != null) {
			return PostingReadResponse.notFetched(link, duplicate); // 이미 그룹에 있는 공고의 페이지는 다시 받지 않는다
		}
		return this.llmFields.fill(this.reader.fromUrl(link, today), user.id(), today);
	}

	/**
	 * 파일(PDF, DOCX, HWPX, HWP · 20MB). 글은 자료 올리기와 같은 코드로 읽고(extraction 의 UploadedFiles, 동시에 읽는 파일 수도
	 * 함께 센다), 붙여넣은 본문처럼 읽는다. 원문 링크가 없어 같은 공고 안내(duplicate)는 없다.
	 */
	@PostMapping(path = "/api/postings/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	PostingReadResponse readFile(@RequestParam("file") MultipartFile file, @RequestParam(required = false) Long groupId,
			@CurrentUser LoginUser user) throws IOException {
		if (groupId != null) {
			this.access.requireMember(groupId, user.id());
		}
		String text;
		try {
			text = this.files.documentText(file);
		}
		catch (ApiException ex) {
			throw (ex.errorCode() instanceof ExtractionErrorCode code) ? new ApiException(fileError(code)) : ex;
		}
		if (text.length() > PostingReadRequest.MAX_TEXT) {
			throw new ApiException(PostingReadErrorCode.FILE_TOO_LARGE);
		}
		LocalDate today = LocalDate.now(this.clock);
		return this.llmFields.fill(this.reader.fromText(text, null, today), user.id(), today);
	}

	/**
	 * 20MB 를 넘는 파일. 실제 서버(Tomcat)는 multipart 의 max-file-size 에서 이 예외를 낸다. multipart 를 늦게 읽게 해 두어
	 * (spring.servlet.multipart.resolve-lazily) 이 컨트롤러의 인자를 만들 때 나므로 여기서 받는다(자료 올리기와 같다).
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> tooLarge() {
		PostingReadErrorCode code = PostingReadErrorCode.FILE_TOO_LARGE;
		return ResponseEntity.status(code.status()).body(ApiError.of(code));
	}

	/** 자료 올리기의 오류를 공고 읽어 오기의 코드로. 메시지는 이 코드의 것(붙여넣기 안내)을 쓴다. */
	private static PostingReadErrorCode fileError(ExtractionErrorCode code) {
		return switch (code) {
			case SOURCE_TOO_LARGE -> PostingReadErrorCode.FILE_TOO_LARGE;
			case SOURCE_UNSUPPORTED_TYPE -> PostingReadErrorCode.FILE_UNSUPPORTED_TYPE;
			case SOURCE_NO_TEXT, SOURCE_EMPTY -> PostingReadErrorCode.FILE_NO_TEXT;
			case SOURCE_UNREADABLE, SOURCE_NOT_FOUND -> PostingReadErrorCode.FILE_UNREADABLE;
		};
	}

}
