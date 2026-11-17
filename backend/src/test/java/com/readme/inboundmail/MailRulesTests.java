package com.readme.inboundmail;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.readme.inboundmail.MailRules.NewPosting;
import com.readme.inboundmail.MailRules.Outcome;
import com.readme.posting.ApplicationStatus;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

import static org.assertj.core.api.Assertions.assertThat;

/** LLM 의 답에서 반영할 것 정하기(메일 설계 4.3, M11). */
class MailRulesTests {

	private static final String BODY = "1차 면접에 합격하셨습니다.\n2차 면접은   11월 17일 오후 2시입니다.";

	private static final Set<Long> MINE = Set.of(7L, 8L);

	private static final ApplicationStatus INTERVIEW_2 = new ApplicationStatus(Decision.APPLIED, Stage.INTERVIEW_2,
			Result.IN_PROGRESS);

	@Test
	void statusMailOnMyPosting() {
		Outcome outcome = decide(answer(7, "", "", "", "interview_2", "in_progress", "2026-11-17", "14:00",
				"2차 면접은 11월 17일 오후 2시입니다.", "예시 보안 면접 안내"));

		assertThat(outcome.postingId()).isEqualTo(7L);
		assertThat(outcome.newPosting()).isNull();
		assertThat(outcome.proposal()).isEqualTo(INTERVIEW_2);
		assertThat(outcome.scheduleDate()).isEqualTo(LocalDate.of(2026, 11, 17));
		assertThat(outcome.scheduleTime()).isEqualTo(LocalTime.of(14, 0));
		assertThat(outcome.quote()).isEqualTo("2차 면접은 11월 17일 오후 2시입니다."); // 띄어쓰기가 달라도 본문에 있다
		assertThat(outcome.summary()).isEqualTo("예시 보안 면접 안내");
	}

	@Test
	void notRecruitingOrUnknownPostingIsDropped() {
		assertThat(MailRules.decide(new MailAnswer(false, 7, "", "", "", "", "", "", "", "", ""), BODY, "s", MINE))
			.isNull();
		assertThat(MailRules.decide(null, BODY, "s", MINE)).isNull();
		// 목록에 없는 id 이고 회사명이나 공고명이 없다
		assertThat(decide(answer(99, "예시 보안", "", "", "", "", "", "", "", ""))).isNull();
		assertThat(decide(answer(0, "", "백엔드", "", "", "", "", "", "", ""))).isNull();
		assertThat(decide(answer(0, "가".repeat(201), "백엔드", "", "", "", "", "", "", ""))).isNull();
		assertThat(decide(answer(0, "예시", "가".repeat(301), "", "", "", "", "", "", ""))).isNull();
	}

	@Test
	void unknownPostingWithCompanyAndTitleIsNew() {
		Outcome outcome = decide(answer(99, " 예시 클라우드 ", "플랫폼 엔지니어 신입", "2026-11-30", "document", "in_progress",
				"", "", "", ""));

		assertThat(outcome.postingId()).isNull();
		assertThat(outcome.newPosting()).isEqualTo(new NewPosting("예시 클라우드", "플랫폼 엔지니어 신입", LocalDate.of(2026, 11, 30)));
		assertThat(outcome.proposal()).isEqualTo(new ApplicationStatus(Decision.APPLIED, Stage.DOCUMENT, Result.IN_PROGRESS));
		assertThat(decide(answer(0, "예시", "공고", "11월 30일", "", "", "", "", "", "")).newPosting().deadline()).isNull();
	}

	@Test
	void stageAndResultMustBothBeKnownValues() {
		assertThat(decide(answer(7, "", "", "", "interview", "in_progress", "2026-11-17", "", "", "")).proposal()).isNull();
		assertThat(decide(answer(7, "", "", "", "test", "", "2026-11-17", "", "", "")).proposal()).isNull();
		assertThat(decide(answer(7, "", "", "", "TEST", "Failed", "", "", "", "")).proposal())
			.isEqualTo(new ApplicationStatus(Decision.APPLIED, Stage.TEST, Result.FAILED));
	}

	@Test
	void scheduleNeedsAProposalAndValidValues() {
		assertThat(decide(answer(7, "", "", "", "", "", "2026-11-17", "14:00", "", "")).scheduleDate()).isNull();
		Outcome badTime = decide(answer(7, "", "", "", "test", "in_progress", "2026-11-17", "오후 2시", "", ""));
		assertThat(badTime.scheduleDate()).isEqualTo(LocalDate.of(2026, 11, 17));
		assertThat(badTime.scheduleTime()).isNull();
		Outcome badDate = decide(answer(7, "", "", "", "test", "in_progress", "11월 17일", "14:00", "", ""));
		assertThat(badDate.scheduleDate()).isNull();
		assertThat(badDate.scheduleTime()).isNull();
	}

	@Test
	void quoteMustBeInTheBody() {
		assertThat(decide(answer(7, "", "", "", "", "", "", "", "최종 합격을 축하합니다.", "")).quote()).isNull();
		assertThat(decide(answer(7, "", "", "", "", "", "", "", "합격".repeat(251), "")).quote()).isNull();
	}

	@Test
	void summaryFallsBackToTheSubject() {
		assertThat(MailRules.decide(answer(7, "", "", "", "", "", "", "", "", " "), BODY, "[예시] 안내", MINE).summary())
			.isEqualTo("[예시] 안내");
		assertThat(MailRules.decide(answer(7, "", "", "", "", "", "", "", "", "가".repeat(101)), BODY, "제".repeat(120), MINE)
			.summary()).isEqualTo("제".repeat(100));
	}

	@Test
	void proposalWorthReviewingWhenItChangesSomething() {
		Outcome withSchedule = decide(answer(7, "", "", "", "interview_2", "in_progress", "2026-11-17", "", "", ""));
		Outcome without = decide(answer(7, "", "", "", "interview_2", "in_progress", "", "", "", ""));
		Outcome none = decide(answer(7, "", "", "", "", "", "", "", "", ""));

		assertThat(MailRules.worthReview(without, INTERVIEW_2)).isFalse(); // 이미 그 상태다
		assertThat(MailRules.worthReview(withSchedule, INTERVIEW_2)).isTrue(); // 새 일정이 있다
		assertThat(MailRules.worthReview(without, null)).isTrue();
		assertThat(MailRules.worthReview(without,
				new ApplicationStatus(Decision.APPLIED, Stage.INTERVIEW_1, Result.PASSED))).isTrue();
		assertThat(MailRules.worthReview(none, null)).isFalse();
	}

	private static Outcome decide(MailAnswer answer) {
		return MailRules.decide(answer, BODY, "제목", MINE);
	}

	private static MailAnswer answer(long postingId, String company, String title, String deadline, String stage,
			String result, String scheduleDate, String scheduleTime, String quote, String summary) {
		return new MailAnswer(true, postingId, company, title, deadline, stage, result, scheduleDate, scheduleTime, quote,
				summary);
	}

}
