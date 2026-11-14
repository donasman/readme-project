package com.readme.grouppost;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.group.GroupAccess;
import com.readme.grouppost.GroupPostRequests.CommentRequest;
import com.readme.grouppost.GroupPostRequests.DeadlineInput;
import com.readme.grouppost.GroupPostRequests.EditRequest;
import com.readme.grouppost.GroupPostRequests.InterestRequest;
import com.readme.grouppost.GroupPostRequests.PostRequest;
import com.readme.posting.DeadlineKind;

/**
 * 그룹 공고 API(설계 4.2). 그룹의 멤버만 부를 수 있고, 멤버가 아니면 그룹이 없을 때와 같은 404 다.
 * 그 그룹에 없거나 내린 공고는 404 POST_NOT_FOUND 다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/posts")
class GroupPostController {

	private final GroupAccess access;

	private final GroupPostQueries queries;

	private final GroupPostService service;

	private final Clock clock;

	GroupPostController(GroupAccess access, GroupPostQueries queries, GroupPostService service, Clock clock) {
		this.access = access;
		this.queries = queries;
		this.service = service;
		this.clock = clock;
	}

	/** 목록. 내린 공고는 빼고 마감된 공고는 넣는다. */
	@GetMapping
	GroupPostsResponse list(@PathVariable long groupId, @CurrentUser LoginUser user) {
		this.access.requireMember(groupId, user.id());
		return new GroupPostsResponse(today(), this.queries.list(groupId, user.id()));
	}

	/** 올리기. 올린 공고를 상세와 같은 모양으로 돌려준다. 이미 이 그룹에 있으면 409 ALREADY_IN_GROUP 과 postId. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	GroupPostDataResponse post(@PathVariable long groupId, @Valid @RequestBody PostRequest request,
			@CurrentUser LoginUser user) {
		if (!request.deadline().isConsistent()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "마감일은 마감이 \"date\"일 때만, 꼭 넣어 주세요");
		}
		long postingId = this.service.post(groupId, user.id(), request.toDraft());
		return detail(groupId, postingId, user);
	}

	@GetMapping("/{postId}")
	GroupPostDataResponse detail(@PathVariable long groupId, @PathVariable long postId, @CurrentUser LoginUser user) {
		this.access.requireMember(groupId, user.id());
		GroupPostDetailResponse post = this.queries.detail(groupId, postId, user.id())
			.orElseThrow(() -> new ApiException(GroupPostErrorCode.POST_NOT_FOUND));
		return new GroupPostDataResponse(today(), post);
	}

	/**
	 * 공고 고치기(설계 4.5). 이 그룹에 올린 사람만 회사명, 공고명, 마감(날짜나 상시)을 고친다. 아니면 403 FORBIDDEN.
	 * 고친 공고를 상세와 같은 모양으로 돌려준다.
	 */
	@PutMapping("/{postId}")
	GroupPostDataResponse edit(@PathVariable long groupId, @PathVariable long postId,
			@Valid @RequestBody EditRequest request, @CurrentUser LoginUser user) {
		DeadlineInput deadline = requireDateOrAlways(request.deadline());
		this.service.edit(groupId, postId, user.id(), request.company().strip(), request.title().strip(),
				deadline.kind(), deadline.date());
		return detail(groupId, postId, user);
	}

	/** 그룹에서 내리기. 올린 사람과 그룹장만. */
	@DeleteMapping("/{postId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void takeDown(@PathVariable long groupId, @PathVariable long postId, @CurrentUser LoginUser user) {
		this.service.takeDown(groupId, postId, user.id());
	}

	/**
	 * 날짜 넣기. 본문은 올리기의 deadline 과 같은 꼴로, 날짜 { "kind": "date", "date": "…" } 나 상시 { "kind": "always" } 다
	 * (화면·API 대응 문서 3절 6번, 2026-11-13 정함). 바뀐 마감을 같은 꼴로 돌려준다.
	 */
	@PutMapping("/{postId}/deadline")
	DeadlineResponse fillDeadline(@PathVariable long groupId, @PathVariable long postId,
			@Valid @RequestBody DeadlineInput deadline, @CurrentUser LoginUser user) {
		requireDateOrAlways(deadline);
		return this.service.fillDeadline(groupId, postId, user.id(), deadline.kind(), deadline.date());
	}

	@PutMapping("/{postId}/interest")
	MyStateResponse interest(@PathVariable long groupId, @PathVariable long postId,
			@Valid @RequestBody InterestRequest request, @CurrentUser LoginUser user) {
		return this.service.setInterest(groupId, postId, user.id(), request.interested());
	}

	@PostMapping("/{postId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	CommentResponse addComment(@PathVariable long groupId, @PathVariable long postId,
			@Valid @RequestBody CommentRequest request, @CurrentUser LoginUser user) {
		return this.service.addComment(groupId, postId, user.id(), request.text());
	}

	/** 댓글 수정. 쓴 사람만. 고친 댓글을 돌려준다. */
	@PutMapping("/{postId}/comments/{commentId}")
	CommentResponse editComment(@PathVariable long groupId, @PathVariable long postId, @PathVariable long commentId,
			@Valid @RequestBody CommentRequest request, @CurrentUser LoginUser user) {
		this.service.editComment(groupId, postId, commentId, user.id(), request.text());
		return this.queries.comment(commentId);
	}

	/** 댓글 삭제. 쓴 사람과 그룹장. */
	@DeleteMapping("/{postId}/comments/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteComment(@PathVariable long groupId, @PathVariable long postId, @PathVariable long commentId,
			@CurrentUser LoginUser user) {
		this.service.deleteComment(groupId, postId, commentId, user.id());
	}

	/** 날짜 넣기와 공고 고치기의 마감: 날짜나 상시만. 아니면 400 INVALID_INPUT. */
	private static DeadlineInput requireDateOrAlways(DeadlineInput deadline) {
		if (deadline.kind() == DeadlineKind.UNKNOWN || !deadline.isConsistent()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT,
					"마감은 날짜({\"kind\": \"date\", \"date\": …})나 상시({\"kind\": \"always\"})로 넣어 주세요");
		}
		return deadline;
	}

	private LocalDate today() {
		return LocalDate.now(this.clock);
	}

}
