package com.readme.posting;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.FixedClockConfiguration.TODAY;
import static com.readme.TestRequests.loginAs;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * GET /api/me/postings, GET /api/me/postings/{postingId}: 내 공고 목록과 상세(설계 4.2).
 */
@IntegrationTest
class MyPostingReadTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Test
	void listHasTodayAndAllMyJobsNewestFirstIncludingClosedOnes() {
		long me = this.fixtures.user("다온");
		long other = this.fixtures.user("가람");
		long group = this.fixtures.group("백엔드 스터디", other, me);
		// 오늘은 서울 날짜 11-13 이다. UTC 로 구하면 11-12 가 되어, 어제 마감한 공고가 오늘 마감으로 보인다.
		long closed = this.fixtures.posting("예시 보안", "주니어 서버 개발", "DATE", TODAY.minusDays(1));
		this.fixtures.post(group, closed, other, NOW);
		this.fixtures.status(me, closed, "APPLIED", "INTERVIEW_1", "IN_PROGRESS");
		// 그룹에 없는 공고(그룹을 나갔거나 그룹에서 내렸다)도 내 공고에 남는다
		long dueToday = this.fixtures.posting("예시 커머스", "백엔드", "DATE", TODAY);
		this.fixtures.status(me, dueToday, "SKIP", null, null);
		// 내 그룹에 있어도 내 공고 행이 없으면 내 공고가 아니다
		long othersJob = this.fixtures.posting("예시 SaaS", "서버 개발자", "ALWAYS", null);
		this.fixtures.post(group, othersJob, other, NOW);
		this.fixtures.status(other, othersJob, "APPLIED", "DOCUMENT", "IN_PROGRESS");

		assertThat(this.mvc.get().uri("/api/me/postings").with(loginAs(me))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{
					  "today": "2026-11-13",
					  "postings": [
					    {
					      "id": %2$d, "company": "예시 커머스", "title": "백엔드",
					      "deadline": { "kind": "date", "date": "2026-11-13" },
					      "decision": "skip", "stage": null, "result": null, "status": null,
					      "groups": []
					    },
					    {
					      "id": %1$d, "company": "예시 보안", "title": "주니어 서버 개발",
					      "deadline": { "kind": "date", "date": "2026-11-12" },
					      "decision": "applied", "stage": "interview_1", "result": "in_progress", "status": "1차 면접 단계",
					      "groups": [ { "id": %3$d, "name": "백엔드 스터디", "mark": "백", "postId": %1$d } ]
					    }
					  ]
					}
					""".formatted(closed, dueToday, group));
	}

	@Test
	void someoneElsesJobIsNotInMyListAndItsDetailIs404() {
		long me = this.fixtures.user("다온");
		long other = this.fixtures.user("가람");
		long group = this.fixtures.group("백엔드 스터디", other, me);
		long othersJob = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(group, othersJob, other, NOW);
		this.fixtures.status(other, othersJob, "APPLIED", "TEST", "PASSED");

		assertThat(this.mvc.get().uri("/api/me/postings").with(loginAs(me))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{ "today": "2026-11-13", "postings": [] }
					""");
		assertThat(this.mvc.get().uri("/api/me/postings/{postingId}", othersJob).with(loginAs(me)))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("POSTING_NOT_FOUND");
		assertThat(this.mvc.get().uri("/api/me/postings/{postingId}", Long.MAX_VALUE).with(loginAs(me)))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri("/api/me/postings/{postingId}", othersJob).with(loginAs(other))).hasStatusOk();
	}

	@Test
	void detailShowsTheJobAndMyInterestCommentsAndDisclosureInEachOfMyGroups() {
		long me = this.fixtures.user("다온");
		long other = this.fixtures.user("가람");
		long first = this.fixtures.group("첫 그룹", me, other);
		long second = this.fixtures.group("둘째 그룹", other, me);
		long takenDown = this.fixtures.group("내린 그룹", me);
		long notMine = this.fixtures.group("남의 그룹", other);
		this.fixtures.disclosure(first, me, "PROGRESS");
		long posting = this.fixtures.posting("예시 커머스", "백엔드 개발자 신입", "DATE", TODAY.plusDays(3));
		this.fixtures.details(posting, "서류 접수 마감", "회사 채용 홈페이지", "https://example.com/jobs/1", """
				[{ "title": "주요 업무", "items": ["주문·결제 서버 API 개발과 운영"] }]
				""");
		this.fixtures.keywords(posting, "Java", "Spring Boot");
		this.fixtures.post(first, posting, me, NOW);
		this.fixtures.post(second, posting, other, NOW);
		this.fixtures.post(takenDown, posting, me, NOW);
		this.fixtures.takeDown(takenDown, posting);
		this.fixtures.post(notMine, posting, other, NOW);
		// 관심은 첫 그룹에서만 켰다. 댓글은 그룹마다 따로 센다
		this.fixtures.interest(first, posting, me);
		this.fixtures.interest(second, posting, other);
		this.fixtures.comment(first, posting, other, "작년 후기", NOW);
		this.fixtures.comment(first, posting, me, "고마워요", NOW);
		this.fixtures.comment(notMine, posting, other, "남의 그룹 댓글", NOW);
		this.fixtures.status(me, posting, "APPLIED", "DOCUMENT", "IN_PROGRESS");

		assertThat(this.mvc.get().uri("/api/me/postings/{postingId}", posting).with(loginAs(me))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{
					  "today": "2026-11-13",
					  "posting": {
					    "id": %1$d, "company": "예시 커머스", "title": "백엔드 개발자 신입",
					    "deadline": { "kind": "date", "date": "2026-11-16" },
					    "decision": "applied", "stage": "document", "result": "in_progress", "status": "지원함",
					    "groups": [
					      { "id": %2$d, "name": "첫 그룹", "mark": "첫", "postId": %1$d },
					      { "id": %3$d, "name": "둘째 그룹", "mark": "둘", "postId": %1$d }
					    ],
					    "applyAt": "회사 채용 홈페이지", "sourceUrl": "https://example.com/jobs/1",
					    "sections": [ { "title": "주요 업무", "items": [ "주문·결제 서버 API 개발과 운영" ] } ],
					    "keywords": [ "Java", "Spring Boot" ], "analysis": null,
					    "memo": null, "matches": [], "unmatched": [], "requirements": [],
					    "groupStates": [
					      { "id": %2$d, "name": "첫 그룹", "mark": "첫", "postId": %1$d,
					        "interested": true, "commentCount": 2, "disclosure": "progress" },
					      { "id": %3$d, "name": "둘째 그룹", "mark": "둘", "postId": %1$d,
					        "interested": false, "commentCount": 0, "disclosure": "applied" }
					    ],
					    "cardPriority": [], "questions": []
					  }
					}
					""".formatted(posting, first, second));
	}

}
