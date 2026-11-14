package com.readme.group;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.FixedClockConfiguration.TODAY;
import static com.readme.TestRequests.loginAs;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * GET /api/groups. 열린 공고 수, 가장 가까운 마감, 멤버가 들어온 날은 한국 날짜로 센다(설계 5.4).
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

		// Fixtures 는 멤버를 NOW 하루 전(2026-11-11T15:30Z)에 넣는다. 한국 날짜로는 11-12 다(UTC 로 구하면 11-11)
		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(me))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				[
				  {
				    "id": %d, "name": "백엔드 스터디", "mark": "백",
				    "members": [
				      { "id": %d, "name": "가람", "joinedAt": "2026-11-12" },
				      { "id": %d, "name": "다온", "joinedAt": "2026-11-12" },
				      { "id": %d, "name": "나래", "joinedAt": "2026-11-12" }
				    ],
				    "leaderId": %d, "openPostCount": 4, "myDisclosure": "progress",
				    "nearestDeadline": { "date": "2026-11-13", "company": "예시 회사", "title": "예시 공고" }
				  }
				]
				""".formatted(group, leader, me, other, leader));
	}

	@Test
	void nearestDeadlineIsTheEarliestDateFromTodayInSeoul() {
		long me = this.fixtures.user("다온");
		long dates = this.fixtures.group("날짜", me);
		// 어제(11-12) 마감은 뺀다. UTC 로 오늘을 구하면 11-12 라서 이것을 고른다
		post(dates, me, "어제 회사", "DATE", TODAY.minusDays(1));
		post(dates, me, "상시 회사", "ALWAYS", null);
		post(dates, me, "확인 필요 회사", "UNKNOWN", null);
		post(dates, me, "모레 회사", "DATE", TODAY.plusDays(2));
		post(dates, me, "오늘 회사", "DATE", TODAY);
		long takenDown = this.fixtures.group("내린 공고", me);
		this.fixtures.takeDown(takenDown, post(takenDown, me, "내린 회사", "DATE", TODAY));
		post(takenDown, me, "내일 회사", "DATE", TODAY.plusDays(1));
		long none = this.fixtures.group("마감 없음", me);
		post(none, me, "상시 회사", "ALWAYS", null);
		post(none, me, "어제 회사", "DATE", TODAY.minusDays(1));

		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(me))).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				[
				  { "id": %d, "nearestDeadline": { "date": "2026-11-13", "company": "오늘 회사", "title": "예시 공고" } },
				  { "id": %d, "nearestDeadline": { "date": "2026-11-14", "company": "내일 회사", "title": "예시 공고" } },
				  { "id": %d, "nearestDeadline": null }
				]
				""".formatted(dates, takenDown, none));
	}

	@Test
	void onTheSameDayTheNearestDeadlineIsTheNewerPostLikeThePostList() {
		long me = this.fixtures.user("다온");
		long group = this.fixtures.group("같은 날", me);
		// 넣는 순서와 올린 시각의 순서를 다르게 둔다. 가장 늦게 올린 공고를 가운데에 넣어,
		// 올린 시각을 보지 않고 id 로만 고르거나(마지막에 넣은 것) 순서 없이 고르면(처음 넣은 것) 다른 공고가 나온다
		postedAt(group, me, "먼저 올린 회사", NOW.minus(Duration.ofHours(2)));
		postedAt(group, me, "나중에 올린 회사", NOW);
		postedAt(group, me, "중간에 올린 회사", NOW.minus(Duration.ofHours(1)));

		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(me))).hasStatusOk().bodyJson()
			.extractingPath("$[0].nearestDeadline.company").isEqualTo("나중에 올린 회사");
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
		return post(group, postedBy, "예시 회사", deadlineKind, deadlineDate);
	}

	private long post(long group, long postedBy, String company, String deadlineKind, LocalDate deadlineDate) {
		long posting = this.fixtures.posting(company, "예시 공고", deadlineKind, deadlineDate);
		this.fixtures.post(group, posting, postedBy, NOW);
		return posting;
	}

	/** 오늘 마감인 공고를 이 시각에 올린다. */
	private void postedAt(long group, long postedBy, String company, Instant postedAt) {
		this.fixtures.post(group, this.fixtures.posting(company, "예시 공고", "DATE", TODAY), postedBy, postedAt);
	}

}
