package com.readme.posting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고(설계 4.3). 지원 관리 화면이 생길 때까지 "지원함" 표시를 넣는 유일한 길이다. 그 화면을 만들 때 주소와 모양을 다시 본다.
 */
@RestController
class MyPostingController {

	private final UserPostings userPostings;

	private final JdbcClient jdbc;

	MyPostingController(UserPostings userPostings, JdbcClient jdbc) {
		this.userPostings = userPostings;
		this.jdbc = jdbc;
	}

	/**
	 * 내 지원 상태 바꾸기. 내 그룹에 올라온 공고이거나 이미 내 공고에 있는 공고만 바꿀 수 있다. 아니면 404 POSTING_NOT_FOUND.
	 * 단계와 결과는 지원함일 때만, 둘을 함께 넣는다. 아니면 400 INVALID_INPUT.
	 */
	@PutMapping("/api/me/postings/{postingId}")
	MyPostingResponse setStatus(@PathVariable long postingId, @Valid @RequestBody StatusRequest request,
			@CurrentUser LoginUser user) {
		ApplicationStatus status = new ApplicationStatus(request.decision(), request.stage(), request.result());
		if (!status.isConsistent()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "단계와 결과는 지원함일 때만, 둘을 함께 넣어 주세요");
		}
		if (!canSee(user.id(), postingId)) {
			throw new ApiException(PostingErrorCode.POSTING_NOT_FOUND);
		}
		this.userPostings.setStatus(user.id(), postingId, status);
		return new MyPostingResponse(postingId, status.decision(), status.stage(), status.result(), status.label());
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

	/** PUT /api/me/postings/{postingId} 의 본문. 예: { "decision": "applied", "stage": "test", "result": "in_progress" } */
	record StatusRequest(@NotNull Decision decision, Stage stage, Result result) {
	}

	/**
	 * 바뀐 내 지원 상태.
	 *
	 * @param status 화면 글자(설계 5.3). 그룹 공고의 me.status 와 같다. 지원함이 아니면 null
	 */
	record MyPostingResponse(long postingId, Decision decision, Stage stage, Result result, String status) {
	}

}
