package com.readme.grouppost;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
import com.readme.group.GroupAccess;
import com.readme.grouppost.GroupPostRequests.CommentRequest;
import com.readme.grouppost.GroupPostRequests.InterestRequest;

/**
 * 그룹 공고 API(설계 4.2). 그룹의 멤버만 부를 수 있고, 멤버가 아니면 그룹이 없을 때와 같은 404 다.
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
		return new GroupPostsResponse(LocalDate.now(this.clock), this.queries.list(groupId, user.id()));
	}

	@GetMapping("/{postId}")
	GroupPostDataResponse detail(@PathVariable long groupId, @PathVariable long postId, @CurrentUser LoginUser user) {
		this.access.requireMember(groupId, user.id());
		GroupPostDetailResponse post = this.queries.detail(groupId, postId, user.id())
			.orElseThrow(() -> new ApiException(GroupPostErrorCode.POST_NOT_FOUND));
		return new GroupPostDataResponse(LocalDate.now(this.clock), post);
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

}
