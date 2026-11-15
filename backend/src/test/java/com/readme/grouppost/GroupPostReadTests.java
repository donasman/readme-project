package com.readme.grouppost;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.FixedClockConfiguration.TODAY;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * 그룹 공고 목록과 상세. 권한(5.1), 공개 범위(5.2), 상태 글자(5.3), 날짜(5.4).
 */
@IntegrationTest
class GroupPostReadTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	// ── 목록 ───────────────────────────────────────────────────────────

	@Test
	void listHasTodayAndPostsNewestFirstWithClosedButWithoutTakenDown() {
		long leader = this.fixtures.user("가람");
		long me = this.fixtures.user("다온");
		long group = this.fixtures.group("백엔드 스터디", leader, me);

		// 서울 11-13 00:10 에 올렸다. UTC 로는 11-12 라서, 날짜를 UTC 로 구하면 postedAt 이 하루 어긋난다.
		long dated = this.fixtures.posting("예시 커머스", "백엔드 개발자 신입", "DATE", TODAY.plusDays(3));
		// 원문 주소는 목록에도 온다(마감일 넣기 창의 원문 링크). 없으면 null 이다
		this.fixtures.details(dated, null, null, "https://example.com/jobs/1", "[]");
		this.fixtures.post(group, dated, leader, Instant.parse("2026-11-12T15:10:00Z"));
		this.fixtures.comment(group, dated, leader, "첫 댓글", NOW);
		this.fixtures.comment(group, dated, me, "둘째 댓글", NOW);
		long always = this.fixtures.posting("예시 SaaS", "서버 개발자", "ALWAYS", null);
		this.fixtures.post(group, always, me, Instant.parse("2026-11-01T03:00:00Z"));
		long closed = this.fixtures.posting("예시 보안", "주니어 서버 개발", "DATE", TODAY.minusDays(7));
		this.fixtures.post(group, closed, leader, Instant.parse("2026-10-25T03:00:00Z"));
		long takenDown = this.fixtures.posting("예시 게임", "내린 공고", "UNKNOWN", null);
		this.fixtures.post(group, takenDown, leader, NOW);
		this.fixtures.takeDown(group, takenDown);

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", group).with(loginAs(me))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{
					  "today": "2026-11-13",
					  "posts": [
					    {
					      "id": %1$d, "groupId": %4$d, "company": "예시 커머스", "title": "백엔드 개발자 신입",
					      "deadline": { "kind": "date", "date": "2026-11-16" },
					      "postedBy": { "id": %5$d, "name": "가람" }, "postedAt": "2026-11-13",
					      "interests": [], "me": { "interested": false, "status": null }, "commentCount": 2,
					      "sourceUrl": "https://example.com/jobs/1"
					    },
					    {
					      "id": %2$d, "groupId": %4$d, "company": "예시 SaaS", "title": "서버 개발자",
					      "deadline": { "kind": "always" },
					      "postedBy": { "id": %6$d, "name": "다온" }, "postedAt": "2026-11-01",
					      "interests": [], "me": { "interested": false, "status": null }, "commentCount": 0,
					      "sourceUrl": null
					    },
					    {
					      "id": %3$d, "groupId": %4$d, "company": "예시 보안", "title": "주니어 서버 개발",
					      "deadline": { "kind": "date", "date": "2026-11-06" },
					      "postedBy": { "id": %5$d, "name": "가람" }, "postedAt": "2026-10-25",
					      "interests": [], "me": { "interested": false, "status": null }, "commentCount": 0,
					      "sourceUrl": null
					    }
					  ]
					}
					""".formatted(dated, always, closed, group, leader, me));
	}

	@Test
	void interestsShowEachMembersApplicationOnlyAsFarAsTheirDisclosure() {
		long leader = this.fixtures.user("가람");
		long me = this.fixtures.user("다온");
		long interestOnly = this.fixtures.user("관심만");
		long appliedOnly = this.fixtures.user("지원여부");
		long progress = this.fixtures.user("단계결과");
		long justApplied = this.fixtures.user("서류중");
		long undecided = this.fixtures.user("미정");
		long notInterested = this.fixtures.user("관심없음");
		long group = this.fixtures.group("공개 범위", leader, me, interestOnly, appliedOnly, progress, justApplied,
				undecided, notInterested);
		this.fixtures.disclosure(group, interestOnly, "INTEREST");
		this.fixtures.disclosure(group, progress, "PROGRESS");
		this.fixtures.disclosure(group, justApplied, "PROGRESS");
		this.fixtures.disclosure(group, undecided, "PROGRESS");
		long posting = this.fixtures.posting("예시 커머스", "백엔드", "DATE", TODAY.plusDays(3));
		this.fixtures.post(group, posting, leader, NOW);

		this.fixtures.interest(group, posting, interestOnly);
		this.fixtures.status(interestOnly, posting, "APPLIED", "TEST", "FAILED");
		this.fixtures.interest(group, posting, appliedOnly);
		this.fixtures.status(appliedOnly, posting, "APPLIED", "INTERVIEW_1", "IN_PROGRESS");
		this.fixtures.interest(group, posting, progress);
		this.fixtures.status(progress, posting, "APPLIED", "INTERVIEW_2", "IN_PROGRESS");
		this.fixtures.interest(group, posting, justApplied);
		this.fixtures.status(justApplied, posting, "APPLIED", "DOCUMENT", "IN_PROGRESS");
		this.fixtures.interest(group, posting, undecided);
		this.fixtures.status(undecided, posting, "UNDECIDED", null, null);
		// 지원했지만 이 그룹에서 관심을 켜지 않은 멤버는 나오지 않는다
		this.fixtures.status(notInterested, posting, "APPLIED", "FINAL", "PASSED");
		// 나는 interests 에서 빠지고 me 로 따로 온다. 내 상태는 공개 범위와 무관하게 다 보인다
		this.fixtures.interest(group, posting, me);
		this.fixtures.status(me, posting, "APPLIED", "TEST", "PASSED");

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", group).with(loginAs(me))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{
					  "today": "2026-11-13",
					  "posts": [
					    {
					      "id": %d, "groupId": %d, "company": "예시 커머스", "title": "백엔드",
					      "deadline": { "kind": "date", "date": "2026-11-16" },
					      "postedBy": { "id": %d, "name": "가람" }, "postedAt": "2026-11-13",
					      "interests": [
					        { "member": { "id": %d, "name": "관심만" }, "applied": false },
					        { "member": { "id": %d, "name": "지원여부" }, "applied": true },
					        { "member": { "id": %d, "name": "단계결과" }, "applied": true, "progress": "2차 면접 단계" },
					        { "member": { "id": %d, "name": "서류중" }, "applied": true },
					        { "member": { "id": %d, "name": "미정" }, "applied": false }
					      ],
					      "me": { "interested": true, "status": "필기 합격" },
					      "commentCount": 0, "sourceUrl": null
					    }
					  ]
					}
					""".formatted(posting, group, leader, interestOnly, appliedOnly, progress, justApplied, undecided));
	}

	@Test
	void sameJobInTwoGroupsKeepsCommentsAndInterestsApartButShowsMyStatusInBoth() {
		long me = this.fixtures.user("다온");
		long first = this.fixtures.group("첫 그룹", me);
		long second = this.fixtures.group("둘째 그룹", me);
		long posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(first, posting, me, NOW);
		this.fixtures.post(second, posting, me, NOW);
		this.fixtures.comment(first, posting, me, "첫 그룹에만 단 댓글", NOW);
		this.fixtures.interest(first, posting, me);
		this.fixtures.status(me, posting, "APPLIED", "DOCUMENT", "IN_PROGRESS");

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", second, posting).with(loginAs(me)))
			.hasStatusOk()
			.bodyJson().isLenientlyEqualTo("""
					{ "post": { "me": { "interested": false, "status": "지원함" }, "commentCount": 0, "comments": [] } }
					""");
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", first, posting).with(loginAs(me)))
			.hasStatusOk()
			.bodyJson().isLenientlyEqualTo("""
					{ "post": { "me": { "interested": true, "status": "지원함" }, "commentCount": 1 } }
					""");
	}

	// ── 상세 ───────────────────────────────────────────────────────────

	@Test
	void detailAddsJobContentKeywordsAndCommentsInKoreanTime() {
		long leader = this.fixtures.user("가람");
		long me = this.fixtures.user("다온");
		long gone = this.fixtures.user("탈퇴");
		long group = this.fixtures.group("백엔드 스터디", leader, me, gone);
		long posting = this.fixtures.posting("예시 커머스", "백엔드 개발자 신입", "DATE", TODAY.plusDays(3));
		this.fixtures.details(posting, "서류 접수 마감", "회사 채용 홈페이지", "https://example.com/jobs/1", """
				[{ "title": "주요 업무", "items": ["주문·결제 서버 API 개발과 운영"] }]
				""");
		this.fixtures.keywords(posting, "Java", "Spring Boot");
		this.fixtures.post(group, posting, gone, NOW);
		long older = this.fixtures.comment(group, posting, gone, "지난주에 지원했어요", Instant.parse("2026-11-08T03:00:00Z"));
		long newer = this.fixtures.comment(group, posting, leader, "후기: https://example.com/review 참고",
				Instant.parse("2026-11-12T15:00:00Z"));
		// 올린 사람과 댓글 쓴 사람이 계정을 지우면 null 로 온다
		this.fixtures.deleteUser(gone);

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", group, posting).with(loginAs(me)))
			.hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{
					  "today": "2026-11-13",
					  "post": {
					    "id": %d, "groupId": %d, "company": "예시 커머스", "title": "백엔드 개발자 신입",
					    "deadline": { "kind": "date", "date": "2026-11-16" },
					    "postedBy": null, "postedAt": "2026-11-13",
					    "interests": [], "me": { "interested": false, "status": null }, "commentCount": 2,
					    "deadlineName": "서류 접수 마감", "applyAt": "회사 채용 홈페이지", "sourceUrl": "https://example.com/jobs/1",
					    "sections": [ { "title": "주요 업무", "items": [ "주문·결제 서버 API 개발과 운영" ] } ],
					    "keywords": [ "Java", "Spring Boot" ], "analysis": null,
					    "comments": [
					      {
					        "id": %d, "author": null, "createdAt": "2026-11-08T12:00:00+09:00",
					        "body": [ { "text": "지난주에 지원했어요" } ]
					      },
					      {
					        "id": %d, "author": { "id": %d, "name": "가람" }, "createdAt": "2026-11-13T00:00:00+09:00",
					        "body": [
					          { "text": "후기: " },
					          { "text": "https://example.com/review", "href": "https://example.com/review" },
					          { "text": " 참고" }
					        ]
					      }
					    ]
					  }
					}
					""".formatted(posting, group, older, newer, leader));
	}

	@Test
	void detailOfAJobWithNothingFilledInHasEmptyContent() {
		long me = this.fixtures.user("다온");
		long group = this.fixtures.group("그룹", me);
		long posting = this.fixtures.posting("예시 회사", "공고", "UNKNOWN", null);
		this.fixtures.post(group, posting, me, NOW);

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", group, posting).with(loginAs(me)))
			.hasStatusOk()
			.bodyJson().isLenientlyEqualTo("""
					{ "post": { "deadline": { "kind": "unknown" }, "deadlineName": null, "applyAt": null, "sourceUrl": null,
					            "sections": [], "keywords": [], "comments": [] } }
					""");
	}

	// ── 권한 ───────────────────────────────────────────────────────────

	@Test
	void nonMemberGetsGroupNotFoundEverywhere() {
		long leader = this.fixtures.user("가람");
		long outsider = this.fixtures.user("남");
		long group = this.fixtures.group("그룹", leader);
		long posting = this.fixtures.posting("예시 회사", "공고", "ALWAYS", null);
		this.fixtures.post(group, posting, leader, NOW);

		assertGroupNotFound(this.mvc.get().uri("/api/groups/{groupId}/posts", group).with(loginAs(outsider)));
		assertGroupNotFound(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", group, posting).with(loginAs(outsider)));
		assertGroupNotFound(this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/interest", group, posting)
			.with(loginAs(outsider)).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{ \"interested\": true }"));
		assertGroupNotFound(this.mvc.post().uri("/api/groups/{groupId}/posts/{postId}/comments", group, posting)
			.with(loginAs(outsider)).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{ \"text\": \"몰래\" }"));
		// 없는 그룹도 같은 응답이다
		assertGroupNotFound(this.mvc.get().uri("/api/groups/{groupId}/posts", Long.MAX_VALUE).with(loginAs(outsider)));
	}

	@Test
	void jobOfAnotherGroupOrTakenDownIsPostNotFound() {
		long me = this.fixtures.user("다온");
		long mine = this.fixtures.group("내 그룹", me);
		long others = this.fixtures.group("남의 그룹", this.fixtures.user("가람"));
		long elsewhere = this.fixtures.posting("예시 회사", "남의 그룹 공고", "ALWAYS", null);
		this.fixtures.post(others, elsewhere, me, NOW);
		long takenDown = this.fixtures.posting("예시 회사", "내린 공고", "ALWAYS", null);
		this.fixtures.post(mine, takenDown, me, NOW);
		this.fixtures.takeDown(mine, takenDown);

		for (long posting : new long[] { elsewhere, takenDown }) {
			assertPostNotFound(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", mine, posting).with(loginAs(me)));
			assertPostNotFound(this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/interest", mine, posting)
				.with(loginAs(me)).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{ \"interested\": true }"));
			assertPostNotFound(this.mvc.post().uri("/api/groups/{groupId}/posts/{postId}/comments", mine, posting)
				.with(loginAs(me)).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{ \"text\": \"댓글\" }"));
		}
	}

	@Test
	void beforeLoginIs401() {
		assertThat(this.mvc.get().uri("/api/groups/1/posts")).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
	}

	private void assertGroupNotFound(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("GROUP_NOT_FOUND");
	}

	private void assertPostNotFound(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("POST_NOT_FOUND");
	}

}
