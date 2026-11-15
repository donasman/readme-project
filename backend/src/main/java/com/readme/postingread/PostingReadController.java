package com.readme.postingread;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.group.GroupAccess;
import com.readme.postingread.PostingReadResponse.Duplicate;

/**
 * 공고 읽어 오기(공고 설계 4.6). 로그인한 사람이면 부를 수 있고, groupId 를 넣으면 그 그룹의 멤버만이다.
 * 읽은 결과는 저장하지 않는다. 읽지 못하면 422 FETCH_BLOCKED·FETCH_FAILED 로 본문 붙여넣기를 안내한다.
 */
@RestController
class PostingReadController {

	private final GroupAccess access;

	private final PostingReadQueries queries;

	private final PostingReader reader;

	private final Clock clock;

	PostingReadController(GroupAccess access, PostingReadQueries queries, PostingReader reader, Clock clock) {
		this.access = access;
		this.queries = queries;
		this.reader = reader;
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
			return this.reader.fromText(request.text(), link, today).with(duplicate);
		}
		if (duplicate != null) {
			return PostingReadResponse.notFetched(link, duplicate); // 이미 그룹에 있는 공고의 페이지는 다시 받지 않는다
		}
		return this.reader.fromUrl(link, today);
	}

}
