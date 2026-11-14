package com.readme.grouppost;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.FixedClockConfiguration.TODAY;
import static com.readme.TestRequests.loginAs;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.common.config.TimeConfig;

/**
 * GET /api/me/group-activity: 홈의 그룹 소식(설계 4.5, 화면·API 대응 문서 3절 14번).
 */
@IntegrationTest
class GroupActivityTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	long leader;

	long me;

	long nara;

	long group;

	@BeforeEach
	void myGroup() {
		this.leader = this.fixtures.user("가람");
		this.me = this.fixtures.user("다온");
		this.nara = this.fixtures.user("나래");
		this.group = this.fixtures.group("백엔드 스터디", this.leader, this.me, this.nara);
	}

	@Test
	void newPostsAndCommentsOfMyGroupsOnlyNewestFirst() {
		long first = job("예시 커머스", this.leader, NOW.minus(Duration.ofHours(1)));
		long second = job("예시 SaaS", this.nara, NOW.minus(Duration.ofDays(2)));
		job("내가 올린 회사", this.me, NOW.minus(Duration.ofMinutes(10)));
		long takenDown = job("내린 회사", this.leader, NOW.minus(Duration.ofMinutes(5)));
		this.fixtures.takeDown(this.group, takenDown);

		// 같은 사람이 같은 공고에 여러 번 달면 한 줄이고, 시각은 마지막 댓글이다
		this.fixtures.comment(this.group, first, this.nara, "첫 댓글", NOW.minus(Duration.ofHours(3)));
		this.fixtures.comment(this.group, first, this.nara, "둘째 댓글", NOW.minus(Duration.ofMinutes(30)));
		this.fixtures.comment(this.group, second, this.leader, "후기 있어요", NOW.minus(Duration.ofDays(1)));
		// 들어가지 않는 것: 내 댓글, 내린 공고의 댓글
		this.fixtures.comment(this.group, first, this.me, "내 댓글", NOW.minus(Duration.ofMinutes(1)));
		this.fixtures.comment(this.group, takenDown, this.nara, "내린 공고의 댓글", NOW.minus(Duration.ofMinutes(1)));

		// 내가 없는 그룹: 같은 공고를 함께 쓰지만, 그 그룹의 공고와 댓글은 들어가지 않는다
		long stranger = this.fixtures.user("모르는 사람");
		long otherGroup = this.fixtures.group("다른 그룹", stranger, this.nara);
		this.fixtures.post(otherGroup, first, stranger, NOW.minus(Duration.ofMinutes(2)));
		this.fixtures.comment(otherGroup, first, this.nara, "다른 그룹의 댓글", NOW.minus(Duration.ofMinutes(1)));
		job(otherGroup, "다른 그룹의 회사", stranger, NOW.minus(Duration.ofMinutes(1)));

		assertThat(this.mvc.get().uri("/api/me/group-activity").with(loginAs(this.me))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					[
					  { "group": { "id": %1$d, "name": "백엔드 스터디", "mark": "백" },
					    "text": "나래 님이 예시 커머스 공고에 댓글을 남겼습니다", "postId": %2$d },
					  { "group": { "id": %1$d, "name": "백엔드 스터디", "mark": "백" },
					    "text": "새 공고 2건", "postId": null },
					  { "group": { "id": %1$d, "name": "백엔드 스터디", "mark": "백" },
					    "text": "가람 님이 예시 SaaS 공고에 댓글을 남겼습니다", "postId": %3$d }
					]
					""".formatted(this.group, first, second));
	}

	@Test
	void theWindowIsTodayAndTheSevenSeoulDaysBefore() {
		// 오늘은 서울 11-13 이다. 11-06 00:00(서울)부터 넣는다. UTC 로 날짜를 구하면 11-05 09:00(서울)부터가 되어 틀린다.
		Instant edge = TODAY.minusDays(7).atStartOfDay(TimeConfig.SEOUL).toInstant();
		assertThat(edge).isEqualTo(Instant.parse("2026-11-05T15:00:00Z"));
		Instant justBefore = edge.minusSeconds(1);

		long inside = job("경계 안 회사", this.nara, edge);
		long outside = job("경계 밖 회사", this.nara, justBefore);
		this.fixtures.comment(this.group, outside, this.nara, "경계에 단 댓글", edge);
		this.fixtures.comment(this.group, inside, this.leader, "경계 바로 앞 댓글", justBefore);

		assertThat(this.mvc.get().uri("/api/me/group-activity").with(loginAs(this.me))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					[
					  { "group": { "id": %1$d, "name": "백엔드 스터디", "mark": "백" },
					    "text": "새 공고 1건", "postId": null },
					  { "group": { "id": %1$d, "name": "백엔드 스터디", "mark": "백" },
					    "text": "나래 님이 경계 밖 회사 공고에 댓글을 남겼습니다", "postId": %2$d }
					]
					""".formatted(this.group, outside));
	}

	@Test
	void statusAndCommentTextNeverLeak() {
		// "관심만" 공개하는 나래가 지원했다. 소식에는 지원 상태도, 댓글 본문(링크 포함)도 실리지 않는다.
		long job = job("예시 보안", this.leader, NOW.minus(Duration.ofDays(3)));
		this.fixtures.disclosure(this.group, this.nara, "INTEREST");
		this.fixtures.interest(this.group, job, this.nara);
		this.fixtures.status(this.nara, job, "APPLIED", "INTERVIEW_2", "PASSED");
		this.fixtures.comment(this.group, job, this.nara, "2차 면접 붙었어요 javascript:alert(1) https://example.com/x",
				NOW.minus(Duration.ofHours(2)));

		assertThat(this.mvc.get().uri("/api/me/group-activity").with(loginAs(this.me))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					[
					  { "group": { "id": %1$d, "name": "백엔드 스터디", "mark": "백" },
					    "text": "나래 님이 예시 보안 공고에 댓글을 남겼습니다", "postId": %2$d },
					  { "group": { "id": %1$d, "name": "백엔드 스터디", "mark": "백" },
					    "text": "새 공고 1건", "postId": null }
					]
					""".formatted(this.group, job));
	}

	@Test
	void atMostTwentyLinesNewestFirst() throws Exception {
		List<Long> jobs = new ArrayList<>();
		for (int i = 1; i <= 21; i++) {
			long job = job("회사 " + i, this.leader, NOW.minus(Duration.ofDays(1)));
			this.fixtures.comment(this.group, job, this.nara, "댓글", NOW.minus(Duration.ofMinutes(i)));
			jobs.add(job);
		}

		MvcTestResult result = this.mvc.get().uri("/api/me/group-activity").with(loginAs(this.me)).exchange();

		assertThat(result).hasStatusOk();
		// 댓글 21줄과 "새 공고 21건"(하루 전) 가운데 최근 20줄: 댓글 1~20분 전
		List<Number> postIds = JsonPath.read(result.getResponse().getContentAsString(), "$[*].postId");
		assertThat(postIds).extracting(Number::longValue).containsExactlyElementsOf(jobs.subList(0, 20));
	}

	@Test
	void nothingWhenIHaveNoGroups() {
		long loner = this.fixtures.user("혼자");
		job("예시 커머스", this.leader, NOW);

		assertThat(this.mvc.get().uri("/api/me/group-activity").with(loginAs(loner))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("[]");
	}

	private long job(String company, long postedBy, Instant postedAt) {
		return job(this.group, company, postedBy, postedAt);
	}

	private long job(long groupId, String company, long postedBy, Instant postedAt) {
		long job = this.fixtures.posting(company, "백엔드", "ALWAYS", null);
		this.fixtures.post(groupId, job, postedBy, postedAt);
		return job;
	}

}
