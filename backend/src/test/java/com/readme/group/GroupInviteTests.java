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
 * 초대 링크 만들기와 수락(설계 4.1, 가정 6: 멤버 누구나, 7일, 인원 제한 없음).
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
		assertThat(link).matches("http://localhost:5173/invites/[A-Za-z0-9_-]{43}");
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

		assertThat(accept(token, newcomer)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{
				  "id": %d, "name": "백엔드 스터디", "mark": "백",
				  "members": [ { "id": %d, "name": "가람" }, { "id": %d, "name": "다온" } ],
				  "leaderId": %d, "openPostCount": 0, "myDisclosure": "applied"
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
		// 시계는 멈춰 있으므로 만료 시각을 지금으로 당긴다
		this.jdbc.sql("update group_invites set expires_at = :now where token_hash = :hash")
			.param("now", Timestamp.from(NOW))
			.param("hash", Tokens.sha256Hex(expired))
			.update();
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

	private MockMvcTester.MockMvcRequestBuilder invite(long groupId, long userId) {
		return this.mvc.post().uri("/api/groups/{groupId}/invites", groupId).with(loginAs(userId)).with(xsrf());
	}

	private MockMvcTester.MockMvcRequestBuilder accept(String token, long userId) {
		return this.mvc.post().uri("/api/group-invites/{token}/accept", token).with(loginAs(userId)).with(xsrf());
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
