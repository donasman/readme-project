package com.readme.posting;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고(설계 4.2): 목록, 그룹 없이 넣기, 상세, 지원 상태 바꾸기, 나만 보는 메모. 조회와 메모는 내 공고 행(user_postings)이 있는 공고만 다룬다.
 */
@RestController
class MyPostingController {

	private final UserPostings userPostings;

	private final MyPostingQueries queries;

	private final JdbcClient jdbc;

	private final Clock clock;

	MyPostingController(UserPostings userPostings, MyPostingQueries queries, JdbcClient jdbc, Clock clock) {
		this.userPostings = userPostings;
		this.queries = queries;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** 내 공고 전부와 서버 기준 오늘. 거르기와 검색은 프런트가 한다. */
	@GetMapping("/api/me/postings")
	PostingsResponse list(@CurrentUser LoginUser user) {
		return new PostingsResponse(LocalDate.now(this.clock), this.queries.list(user.id()));
	}

	/**
	 * 그룹 없이 내 공고에 넣기. 본문은 그룹에 올리기와 같다. 넣은 공고를 상세와 같은 모양으로 돌려준다.
	 * 이미 내 공고에 있으면 409 ALREADY_IN_MY_POSTINGS 와 postingId.
	 */
	@PostMapping("/api/me/postings")
	@ResponseStatus(HttpStatus.CREATED)
	PostingResponse add(@Valid @RequestBody UploadRequest request, @CurrentUser LoginUser user) {
		long postingId = this.userPostings.add(user.id(), request.toDraft());
		return detail(postingId, user);
	}

	/** 내 공고 상세. 내 공고에 없는 공고면 404 POSTING_NOT_FOUND. */
	@GetMapping("/api/me/postings/{postingId}")
	PostingResponse detail(@PathVariable long postingId, @CurrentUser LoginUser user) {
		MyPostingDetailResponse posting = this.queries.detail(user.id(), postingId)
			.orElseThrow(() -> new ApiException(PostingErrorCode.POSTING_NOT_FOUND));
		return new PostingResponse(LocalDate.now(this.clock), posting);
	}

	/**
	 * 내 지원 상태 바꾸기. 내 그룹에 올라온 공고이거나 이미 내 공고에 있는 공고만 바꿀 수 있다. 아니면 404 POSTING_NOT_FOUND.
	 * 단계와 결과는 지원함일 때만, 둘을 함께 넣는다. 아니면 400 INVALID_INPUT. 메모는 건드리지 않는다.
	 */
	@PutMapping("/api/me/postings/{postingId}")
	StatusResponse setStatus(@PathVariable long postingId, @Valid @RequestBody StatusRequest request,
			@CurrentUser LoginUser user) {
		ApplicationStatus status = new ApplicationStatus(request.decision(), request.stage(), request.result());
		if (!status.isConsistent()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "단계와 결과는 지원함일 때만, 둘을 함께 넣어 주세요");
		}
		if (!canSee(user.id(), postingId)) {
			throw new ApiException(PostingErrorCode.POSTING_NOT_FOUND);
		}
		this.userPostings.setStatus(user.id(), postingId, status);
		return new StatusResponse(postingId, status.decision(), status.stage(), status.result(), status.label());
	}

	/**
	 * 나만 보는 메모. 빈 메모는 지운다(null 로 둔다). 지원 상태는 건드리지 않는다.
	 * 내 공고에 없는 공고면 404 POSTING_NOT_FOUND.
	 */
	@PutMapping("/api/me/postings/{postingId}/memo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setMemo(@PathVariable long postingId, @Valid @RequestBody MemoRequest request, @CurrentUser LoginUser user) {
		if (!this.userPostings.setMemo(user.id(), postingId, request.memo())) {
			throw new ApiException(PostingErrorCode.POSTING_NOT_FOUND);
		}
	}

	private boolean canSee(long userId, long postingId) {
		return this.jdbc.sql("""
				select exists (select 1 from user_postings where user_id = :userId and posting_id = :postingId)
				    or exists (select 1
				                 from group_posts gp
				                 join group_members m on m.group_id = gp.group_id
				                where gp.posting_id = :postingId and m.user_id = :userId and gp.taken_down_at is null)
				""")
			.param("userId", userId)
			.param("postingId", postingId)
			.query(Boolean.class)
			.single();
	}

	/**
	 * GET /api/me/postings 의 응답. 프런트 getMyPostings 의 모양이다.
	 *
	 * @param today 서버의 한국 날짜. 프런트가 D-day 와 마감됨을 이 날로 센다
	 */
	record PostingsResponse(LocalDate today, List<MyPostingResponse> postings) {
	}

	/** GET /api/me/postings/{postingId} 의 응답. 프런트 getMyPosting 의 모양이다. */
	record PostingResponse(LocalDate today, MyPostingDetailResponse posting) {
	}

	/** PUT /api/me/postings/{postingId} 의 본문. 예: { "decision": "applied", "stage": "test", "result": "in_progress" } */
	record StatusRequest(@NotNull Decision decision, Stage stage, Result result) {
	}

	/**
	 * 바뀐 내 지원 상태.
	 *
	 * @param status 화면 글자(설계 5.3). 그룹 공고의 me.status 와 같다. 지원함이 아니면 null
	 */
	record StatusResponse(long postingId, Decision decision, Stage stage, Result result, String status) {
	}

	/** PUT /api/me/postings/{postingId}/memo 의 본문. 길이는 user_postings.memo 칸과 같다. 비었거나 null 이면 메모를 지운다. */
	record MemoRequest(@Size(max = 2000) String memo) {
	}

}
