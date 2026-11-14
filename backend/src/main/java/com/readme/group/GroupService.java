package com.readme.group;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.config.AppProperties;
import com.readme.common.config.TimeConfig;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.common.token.Tokens;
import com.readme.group.GroupRequests.InviteResponse;

/**
 * 그룹을 바꾸는 일. 응답에 실을 그룹은 이 클래스의 트랜잭션이 끝난 뒤 컨트롤러가 GroupQueries 로 읽는다.
 */
@Service
class GroupService {

	/** 초대 링크가 쓸 수 있는 기간(설계 가정 6). */
	static final Duration INVITE_LIFETIME = Duration.ofDays(7);

	private final GroupAccess access;

	private final GroupRepository groups;

	private final GroupMemberRepository members;

	private final GroupInviteRepository invites;

	private final AppProperties app;

	private final Clock clock;

	GroupService(GroupAccess access, GroupRepository groups, GroupMemberRepository members,
			GroupInviteRepository invites, AppProperties app, Clock clock) {
		this.access = access;
		this.groups = groups;
		this.members = members;
		this.invites = invites;
		this.app = app;
		this.clock = clock;
	}

	/** 그룹을 만든다. 만든 사람이 그룹장이 된다. 새 그룹의 id 를 돌려준다. */
	@Transactional
	long create(long userId, String name, String mark) {
		Instant now = this.clock.instant();
		String groupName = name.strip();
		String groupMark = (mark == null || mark.isBlank()) ? firstCharacter(groupName) : mark.strip();
		Group group = this.groups.save(new Group(groupName, groupMark, userId, now));
		this.members.save(GroupMember.leader(group.getId(), userId, now));
		return group.getId();
	}

	/** 이 그룹에 보일 내 공개 범위를 바꾼다. */
	@Transactional
	void changeDisclosure(long groupId, long userId, Disclosure disclosure) {
		this.access.requireMember(groupId, userId);
		GroupMember member = this.members.findById(new GroupMember.Key(groupId, userId)).orElseThrow();
		member.changeDisclosure(disclosure);
	}

	/**
	 * 그룹을 나간다. 그룹장은 나갈 수 없다(409 LEADER_CANNOT_LEAVE).
	 * 이 그룹의 내 관심은 DB 가 함께 지운다. 내가 쓴 댓글, 올린 공고, 내 공고(지원 상태)는 남는다.
	 */
	@Transactional
	void leave(long groupId, long userId) {
		if (this.access.requireMember(groupId, userId).leader()) {
			throw new ApiException(GroupErrorCode.LEADER_CANNOT_LEAVE);
		}
		this.members.deleteById(new GroupMember.Key(groupId, userId));
	}

	/** 초대 링크를 만든다. 멤버 누구나 만들고, 7일 동안 여러 사람이 쓸 수 있다(설계 가정 6). */
	@Transactional
	InviteResponse createInvite(long groupId, long userId) {
		this.access.requireMember(groupId, userId);
		Instant now = this.clock.instant();
		// 응답과 DB 에 같은 값이 가도록 초 단위로 자른다
		Instant expiresAt = now.plus(INVITE_LIFETIME).truncatedTo(ChronoUnit.SECONDS);
		String token = Tokens.newToken();
		this.invites.save(new GroupInvite(groupId, Tokens.sha256Hex(token), userId, expiresAt, now));
		return new InviteResponse(inviteLink(token), expiresAt.atZone(TimeConfig.SEOUL).toOffsetDateTime());
	}

	/**
	 * 초대를 받아 멤버가 된다. 들어간 그룹의 id 를 돌려준다.
	 * 없는 링크는 404 INVITE_NOT_FOUND. 이미 멤버면 링크가 만료되었어도 그대로 성공한다.
	 * 멤버가 아니고 링크가 만료·취소되었으면 410 INVITE_EXPIRED.
	 */
	@Transactional
	long acceptInvite(String token, long userId) {
		GroupInvite invite = this.invites.findByTokenHash(Tokens.sha256Hex(token))
			.orElseThrow(() -> new ApiException(GroupErrorCode.INVITE_NOT_FOUND));
		long groupId = invite.groupId();
		if (this.members.existsById(new GroupMember.Key(groupId, userId))) {
			return groupId;
		}
		Instant now = this.clock.instant();
		if (!invite.usableAt(now)) {
			throw new ApiException(GroupErrorCode.INVITE_EXPIRED);
		}
		this.members.insertMemberIfAbsent(groupId, userId, now);
		return groupId;
	}

	/**
	 * 초대 링크를 취소한다. 만든 사람과 그룹장만 한다(아니면 403 FORBIDDEN). 그 그룹에 없는 초대는 404 INVITE_NOT_FOUND.
	 * 이미 취소했거나 만료된 링크도 그대로 성공한다.
	 */
	@Transactional
	void revokeInvite(long groupId, long inviteId, long userId) {
		GroupMembership membership = this.access.requireMember(groupId, userId);
		GroupInvite invite = this.invites.findById(inviteId)
			.filter((found) -> found.groupId() == groupId)
			.orElseThrow(() -> new ApiException(GroupErrorCode.INVITE_NOT_FOUND));
		if (!membership.leader() && !invite.isCreatedBy(userId)) {
			throw new ApiException(CommonErrorCode.FORBIDDEN, "링크를 만든 사람과 그룹장만 취소할 수 있습니다");
		}
		invite.revoke(this.clock.instant());
	}

	private String inviteLink(String token) {
		String origin = this.app.frontendOrigin().replaceAll("/+$", "");
		return origin + "/invites/" + token;
	}

	private static String firstCharacter(String text) {
		return new String(Character.toChars(text.codePointAt(0)));
	}

}
