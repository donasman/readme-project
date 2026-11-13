package com.readme.group;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.FixedClockConfiguration.TODAY;
import static com.readme.TestRequests.loginAs;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * GET /api/groups. 열린 공고 수는 한국 날짜로 센다(설계 5.4).
 */
@IntegrationTest
class GroupControllerTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Test
	void myGroupsWithMembersLeaderOpenPostCountAndMyDisclosure() {
		long leader = this.fixtures.user("가람");
		long me = this.fixtures.user("다온");
		long other = this.fixtures.user("나래");
		long stranger = this.fixtures.user("라온");
		long group = this.fixtures.group("백엔드 스터디", leader, me, other);
		this.fixtures.disclosure(group, me, "PROGRESS");
		this.fixtures.group("남의 그룹", stranger);

		// 열린 공고 4개: 마감일 당일(진행 중), 내일, 상시, 마감일 확인 필요
		post(group, leader, "DATE", TODAY);
		post(group, leader, "DATE", TODAY.plusDays(1));
		post(group, leader, "ALWAYS", null);
		post(group, leader, "UNKNOWN", null);
		// 세지 않는 것: 어제 마감(UTC 로 날짜를 잘못 구하면 오늘로 보고 센다), 그룹에서 내린 공고
		post(group, leader, "DATE", TODAY.minusDays(1));
		this.fixtures.takeDown(group, post(group, leader, "DATE", TODAY.plusDays(3)));

		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(me))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				[
				  {
				    "id": %d, "name": "백엔드 스터디", "mark": "백",
				    "members": [ { "id": %d, "name": "가람" }, { "id": %d, "name": "다온" }, { "id": %d, "name": "나래" } ],
				    "leaderId": %d, "openPostCount": 4, "myDisclosure": "progress"
				  }
				]
				""".formatted(group, leader, me, other, leader));
	}

	@Test
	void groupsComeInTheOrderIJoined() {
		long me = this.fixtures.user("다온");
		long first = this.fixtures.group("먼저", me);
		long second = this.fixtures.group("나중", this.fixtures.user("가람"), me);

		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(me))).hasStatusOk()
			.bodyJson().extractingPath("$[*].id").asArray()
			.extracting((id) -> ((Number) id).longValue())
			.containsExactly(first, second);
	}

	@Test
	void leaderIdIsNullWhenTheLeaderDeletedTheAccount() {
		long leader = this.fixtures.user("가람");
		long me = this.fixtures.user("다온");
		long group = this.fixtures.group("그룹장 없음", leader, me);
		this.fixtures.deleteUser(leader);

		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(me))).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				[ { "id": %d, "members": [ { "id": %d, "name": "다온" } ], "leaderId": null } ]
				""".formatted(group, me));
	}

	@Test
	void noGroupsIsAnEmptyList() {
		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(this.fixtures.user("혼자"))))
			.hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
	}

	@Test
	void beforeLoginIs401() {
		assertThat(this.mvc.get().uri("/api/groups")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private long post(long group, long postedBy, String deadlineKind, LocalDate deadlineDate) {
		long posting = this.fixtures.posting("예시 회사", "예시 공고", deadlineKind, deadlineDate);
		this.fixtures.post(group, posting, postedBy, NOW);
		return posting;
	}

}
