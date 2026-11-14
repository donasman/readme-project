package com.readme.group;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.readme.common.token.Tokens;
import com.readme.group.GroupQueries.InvitePreview;
import com.readme.group.GroupRequests.CreateGroupRequest;
import com.readme.group.GroupRequests.DisclosureRequest;
import com.readme.group.GroupRequests.InvitePreviewResponse;
import com.readme.group.GroupRequests.InviteResponse;
import com.readme.group.GroupRequests.LiveInviteResponse;

/**
 * 그룹 API(설계 4절). 그룹 안의 것은 멤버만 다룰 수 있고, 멤버가 아니면 그룹이 없을 때와 같은 404 다.
 * 초대 미리 보기만 로그인 없이 연다(SecurityConfig).
 */
@RestController
class GroupController {

	private final GroupAccess access;

	private final GroupQueries queries;

	private final GroupService service;

	private final Clock clock;

	GroupController(GroupAccess access, GroupQueries queries, GroupService service, Clock clock) {
		this.access = access;
		this.queries = queries;
		this.service = service;
		this.clock = clock;
	}

	/** 내가 속한 그룹들. 들어간 순서다. 프런트의 첫 화면은 첫 그룹으로 간다. */
	@GetMapping("/api/groups")
	List<GroupResponse> myGroups(@CurrentUser LoginUser user) {
		return this.queries.findMyGroups(user.id(), today());
	}

	/** 그룹 만들기. 만든 사람이 그룹장이 된다. 만든 그룹을 GET /api/groups 의 한 항목 모양으로 돌려준다. */
	@PostMapping("/api/groups")
	@ResponseStatus(HttpStatus.CREATED)
	GroupResponse create(@Valid @RequestBody CreateGroupRequest request, @CurrentUser LoginUser user) {
		long groupId = this.service.create(user.id(), request.name(), request.mark());
		return myGroup(groupId, user);
	}

	/** 이 그룹에 보일 내 공개 범위 바꾸기. 바뀐 그룹을 돌려준다. */
	@PutMapping("/api/groups/{groupId}/me/disclosure")
	GroupResponse changeDisclosure(@PathVariable long groupId, @Valid @RequestBody DisclosureRequest request,
			@CurrentUser LoginUser user) {
		this.service.changeDisclosure(groupId, user.id(), request.disclosure());
		return myGroup(groupId, user);
	}

	/** 그룹 나가기. 그룹장이면 409 LEADER_CANNOT_LEAVE. */
	@DeleteMapping("/api/groups/{groupId}/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void leave(@PathVariable long groupId, @CurrentUser LoginUser user) {
		this.service.leave(groupId, user.id());
	}

	/** 초대 링크 만들기. 멤버 누구나 만든다. */
	@PostMapping("/api/groups/{groupId}/invites")
	@ResponseStatus(HttpStatus.CREATED)
	InviteResponse createInvite(@PathVariable long groupId, @CurrentUser LoginUser user) {
		return this.service.createInvite(groupId, user.id());
	}

	/** 쓸 수 있는(만료·취소되지 않은) 초대 링크들. 멤버만 본다. 링크 자체는 다시 보여 줄 수 없다. */
	@GetMapping("/api/groups/{groupId}/invites")
	List<LiveInviteResponse> liveInvites(@PathVariable long groupId, @CurrentUser LoginUser user) {
		this.access.requireMember(groupId, user.id());
		return this.queries.findLiveInvites(groupId, this.clock.instant());
	}

	/** 초대 링크 취소. 만든 사람과 그룹장만. 이미 취소한 링크도 204 다. */
	@DeleteMapping("/api/groups/{groupId}/invites/{inviteId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revokeInvite(@PathVariable long groupId, @PathVariable long inviteId, @CurrentUser LoginUser user) {
		this.service.revokeInvite(groupId, inviteId, user.id());
	}

	/**
	 * 초대 미리 보기. 로그인하지 않아도 본다. 그룹 이름, 표시 글자, 만든 사람의 이름, 멤버 수만 싣는다.
	 * 없는 링크는 404 INVITE_NOT_FOUND, 만료·취소된 링크는 410 INVITE_EXPIRED.
	 */
	@GetMapping("/api/group-invites/{token}")
	InvitePreviewResponse previewInvite(@PathVariable String token) {
		InvitePreview invite = this.queries.findInvitePreview(Tokens.sha256Hex(token), this.clock.instant())
			.orElseThrow(() -> new ApiException(GroupErrorCode.INVITE_NOT_FOUND));
		if (!invite.usable()) {
			throw new ApiException(GroupErrorCode.INVITE_EXPIRED);
		}
		return invite.response();
	}

	/** 초대 수락. 로그인한 사람이 그 그룹의 멤버가 되고, 들어간 그룹을 돌려준다. */
	@PostMapping("/api/group-invites/{token}/accept")
	GroupResponse acceptInvite(@PathVariable String token, @CurrentUser LoginUser user) {
		long groupId = this.service.acceptInvite(token, user.id());
		return myGroup(groupId, user);
	}

	private GroupResponse myGroup(long groupId, LoginUser user) {
		return this.queries.findMyGroup(user.id(), groupId, today()).orElseThrow();
	}

	private LocalDate today() {
		return LocalDate.now(this.clock);
	}

}
