package com.readme.group;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.common.token.Tokens;

/**
 * 초대 링크 만들기, 미리 보기, 살아 있는 초대 보기와 취소, 수락(설계 4절, 가정 G1: 멤버 누구나, 7일, 인원 제한 없음).
 */
@IntegrationTest
class GroupInviteTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Test
	void anyMemberMakesALinkThatLastsSevenDaysAndOnlyItsHashIsStored() throws Exception {
		long member = this.fixtures.user("다온");
		long group = this.fixtures.group("그룹", this.fixtures.user("가람"), member);

		MvcTestResult result = invite(group, member).exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson()
			.extractingPath("$.expiresAt").isEqualTo("2026-11-20T00:30:00+09:00");
		String link = JsonPath.read(result.getResponse().getContentAsString(), "$.link");
		assertThat(link).matches("http://localhost:5173/invite/[A-Za-z0-9_-]{43}");
		String token = link.substring(link.lastIndexOf('/') + 1);
		String stored = this.jdbc.sql("select token_hash from group_invites where group_id = :groupId")
			.param("groupId", group).query(String.class).single();
		assertThat(stored).isEqualTo(Tokens.sha256Hex(token)).isNotEqualTo(token);
	}

	@Test
	void acceptingJoinsTheGroupAsAMemberWithTheDefaultDisclosure() {
		long leader = this.fixtures.user("가람");
		long group = this.fixtures.group("백엔드 스터디", leader);
		String token = tokenOf(invite(group, leader).exchange());
		long newcomer = this.fixtures.user("다온");

		// 들어온 날은 한국 날짜다. 수락한 때(NOW)는 서울 11-13 00:30 이고 UTC 로는 11-12 다
		assertThat(accept(token, newcomer)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{
				  "id": %d, "name": "백엔드 스터디", "mark": "백",
				  "members": [
				    { "id": %d, "name": "가람", "joinedAt": "2026-11-12" },
				    { "id": %d, "name": "다온", "joinedAt": "2026-11-13" }
				  ],
				  "leaderId": %d, "openPostCount": 0, "myDisclosure": "applied", "nearestDeadline": null
				}
				""".formatted(group, leader, newcomer, leader));
	}

	@Test
	void oneLinkWorksForManyPeopleAndTwiceForTheSamePerson() {
		long leader = this.fixtures.user("가람");
		long group = this.fixtures.group("그룹", leader);
		String token = tokenOf(invite(group, leader).exchange());
		long first = this.fixtures.user("다온");
		long second = this.fixtures.user("나래");

		assertThat(accept(token, first)).hasStatusOk();
		assertThat(accept(token, first)).hasStatusOk();
		assertThat(accept(token, second)).hasStatusOk();
		assertThat(this.jdbc.sql("select count(*) from group_members where group_id = :groupId").param("groupId", group)
			.query(Integer.class).single()).isEqualTo(3);
	}

	@Test
	void expiredOrRevokedLinkIs410ButAMemberStillGetsIn() {
		long leader = this.fixtures.user("가람");
		long member = this.fixtures.user("다온");
		long group = this.fixtures.group("그룹", leader, member);
		String expired = tokenOf(invite(group, leader).exchange());
		expire(expired);
		String revoked = tokenOf(invite(group, leader).exchange());
		this.jdbc.sql("update group_invites set revoked_at = now() where token_hash = :hash")
			.param("hash", Tokens.sha256Hex(revoked))
			.update();

		for (String token : new String[] { expired, revoked }) {
			assertThat(accept(token, this.fixtures.user("남"))).hasStatus(HttpStatus.GONE)
				.bodyJson().extractingPath("$.code").isEqualTo("INVITE_EXPIRED");
			assertThat(accept(token, member)).hasStatusOk();
		}
	}

	@Test
	void unknownLinkIs404() {
		assertThat(accept(Tokens.newToken(), this.fixtures.user("다온"))).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("INVITE_NOT_FOUND");
	}

	@Test
	void nonMemberCannotMakeALinkAndAcceptingNeedsLogin() {
		long group = this.fixtures.group("그룹", this.fixtures.user("가람"));

		assertThat(invite(group, this.fixtures.user("남"))).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("GROUP_NOT_FOUND");
		assertThat(this.mvc.post().uri("/api/group-invites/{token}/accept", Tokens.newToken()).with(xsrf()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ── 미리 보기 ──────────────────────────────────────────────────────

	@Test
	void previewOpensWithoutLoginAndShowsOnlyWhatTheInvitePageNeeds() {
		long leader = this.fixtures.user("가람");
		long member = this.fixtures.user("다온");
		long group = this.fixtures.group("백엔드 스터디", leader, member);
		String token = tokenOf(invite(group, member).exchange());
		String expected = """
				{ "groupName": "백엔드 스터디", "mark": "백", "inviterName": "다온", "memberCount": 2 }
				""";

		assertThat(preview(token)).hasStatusOk().bodyJson().isStrictlyEqualTo(expected);
		assertThat(preview(token).with(loginAs(this.fixtures.user("남")))).hasStatusOk().bodyJson()
			.isStrictlyEqualTo(expected);
	}

	@Test
	void previewOfAnExpiredOrRevokedLinkIs410AndOfAnUnknownLinkIs404() {
		long leader = this.fixtures.user("가람");
		long group = this.fixtures.group("그룹", leader);
		String expired = tokenOf(invite(group, leader).exchange());
		expire(expired);
		String revoked = tokenOf(invite(group, leader).exchange());
		assertThat(revoke(group, inviteId(revoked), leader)).hasStatus(HttpStatus.NO_CONTENT);

		for (String token : new String[] { expired, revoked }) {
			assertThat(preview(token)).hasStatus(HttpStatus.GONE)
				.bodyJson().extractingPath("$.code").isEqualTo("INVITE_EXPIRED");
		}
		assertThat(preview(Tokens.newToken())).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("INVITE_NOT_FOUND");
	}

	// ── 살아 있는 초대 보기와 취소 ─────────────────────────────────────────

	@Test
	void membersSeeOnlyLiveInvitesOfTheGroupNewestFirst() {
		long leader = this.fixtures.user("가람");
		long member = this.fixtures.user("다온");
		long group = this.fixtures.group("그룹", leader, member);
		long first = inviteId(tokenOf(invite(group, member).exchange()));
		long second = inviteId(tokenOf(invite(group, leader).exchange()));
		expire(tokenOf(invite(group, leader).exchange()));
		long revoked = inviteId(tokenOf(invite(group, member).exchange()));
		assertThat(revoke(group, revoked, member)).hasStatus(HttpStatus.NO_CONTENT);
		long other = this.fixtures.user("나래");
		invite(this.fixtures.group("남의 그룹", other), other).exchange();

		// 시계가 멈춰 있어 만든 시각이 모두 같다. 같으면 나중에 만든 것(id 가 큰 것)이 앞이다
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/invites", group).with(loginAs(member))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					[
					  { "id": %d, "createdBy": { "id": %d, "name": "가람" },
					    "createdAt": "2026-11-13T00:30:00+09:00", "expiresAt": "2026-11-20T00:30:00+09:00" },
					  { "id": %d, "createdBy": { "id": %d, "name": "다온" },
					    "createdAt": "2026-11-13T00:30:00+09:00", "expiresAt": "2026-11-20T00:30:00+09:00" }
					]
					""".formatted(second, leader, first, member));
	}

	@Test
	void nonMemberCannotSeeOrRevokeInvites() {
		long leader = this.fixtures.user("가람");
		long group = this.fixtures.group("그룹", leader);
		long invite = inviteId(tokenOf(invite(group, leader).exchange()));
		long stranger = this.fixtures.user("남");

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/invites", group).with(loginAs(stranger)))
			.hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("GROUP_NOT_FOUND");
		assertThat(revoke(group, invite, stranger)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("GROUP_NOT_FOUND");
	}

	@Test
	void creatorAndLeaderRevokeAndARevokedLinkCannotBeAccepted() {
		long leader = this.fixtures.user("가람");
		long member = this.fixtures.user("다온");
		long group = this.fixtures.group("그룹", leader, member);
		String byCreator = tokenOf(invite(group, member).exchange());
		String byLeader = tokenOf(invite(group, member).exchange());

		assertThat(revoke(group, inviteId(byCreator), member)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(revoke(group, inviteId(byLeader), leader)).hasStatus(HttpStatus.NO_CONTENT);

		for (String token : new String[] { byCreator, byLeader }) {
			assertThat(accept(token, this.fixtures.user("새 사람"))).hasStatus(HttpStatus.GONE)
				.bodyJson().extractingPath("$.code").isEqualTo("INVITE_EXPIRED");
		}
	}

	@Test
	void anotherMemberCannotRevoke() {
		long creator = this.fixtures.user("다온");
		long other = this.fixtures.user("나래");
		long group = this.fixtures.group("그룹", this.fixtures.user("가람"), creator, other);
		String token = tokenOf(invite(group, creator).exchange());

		assertThat(revoke(group, inviteId(token), other)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
		assertThat(preview(token)).hasStatusOk();
	}

	@Test
	void revokingTwiceIsStill204() {
		long leader = this.fixtures.user("가람");
		long group = this.fixtures.group("그룹", leader);
		long invite = inviteId(tokenOf(invite(group, leader).exchange()));

		assertThat(revoke(group, invite, leader)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(revoke(group, invite, leader)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void unknownInviteOrAnInviteOfAnotherGroupIs404() {
		long leader = this.fixtures.user("가람");
		long group = this.fixtures.group("그룹", leader);
		String elsewhere = tokenOf(invite(this.fixtures.group("다른 그룹", leader), leader).exchange());

		assertThat(revoke(group, inviteId(elsewhere), leader)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("INVITE_NOT_FOUND");
		assertThat(revoke(group, Long.MAX_VALUE, leader)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("INVITE_NOT_FOUND");
		assertThat(preview(elsewhere)).hasStatusOk();
	}

	private MockMvcTester.MockMvcRequestBuilder invite(long groupId, long userId) {
		return this.mvc.post().uri("/api/groups/{groupId}/invites", groupId).with(loginAs(userId)).with(xsrf());
	}

	private MockMvcTester.MockMvcRequestBuilder accept(String token, long userId) {
		return this.mvc.post().uri("/api/group-invites/{token}/accept", token).with(loginAs(userId)).with(xsrf());
	}

	private MockMvcTester.MockMvcRequestBuilder preview(String token) {
		return this.mvc.get().uri("/api/group-invites/{token}", token);
	}

	private MockMvcTester.MockMvcRequestBuilder revoke(long groupId, long inviteId, long userId) {
		return this.mvc.delete().uri("/api/groups/{groupId}/invites/{inviteId}", groupId, inviteId)
			.with(loginAs(userId)).with(xsrf());
	}

	private long inviteId(String token) {
		return this.jdbc.sql("select id from group_invites where token_hash = :hash")
			.param("hash", Tokens.sha256Hex(token))
			.query(Long.class)
			.single();
	}

	/** 시계는 멈춰 있으므로 만료 시각을 지금으로 당긴다. */
	private void expire(String token) {
		this.jdbc.sql("update group_invites set expires_at = :now where token_hash = :hash")
			.param("now", Timestamp.from(NOW))
			.param("hash", Tokens.sha256Hex(token))
			.update();
	}

	private static String tokenOf(MvcTestResult result) {
		try {
			String link = JsonPath.read(result.getResponse().getContentAsString(), "$.link");
			return link.substring(link.lastIndexOf('/') + 1);
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
