package com.readme.inboundmail;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

import com.readme.posting.ApplicationStatus;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * LLM 의 답에서 반영할 것을 정한다(메일 설계 4.3, M11). Spring 없이 돈다. 답은 믿지 않고 값마다 확인한다.
 */
final class MailRules {

	/** postings.company, postings.title 의 길이 */
	static final int MAX_COMPANY = 200;

	static final int MAX_TITLE = 300;

	static final int MAX_QUOTE = 500;

	static final int MAX_SUMMARY = 100;

	private MailRules() {
	}

	/**
	 * 반영할 것. postingId 와 newPosting 가운데 하나만 있다.
	 *
	 * @param proposal     확인 필요로 둘 지원 상태. 없으면 null
	 * @param scheduleDate 제안한 단계의 날짜. 제안이 없으면 null
	 * @param quote        근거 문장. 본문에 그대로 없으면 null
	 */
	record Outcome(Long postingId, NewPosting newPosting, ApplicationStatus proposal, LocalDate scheduleDate,
			LocalTime scheduleTime, String quote, String summary) {
	}

	/** 메일로 새로 넣을 공고. deadline 은 없으면 null */
	record NewPosting(String company, String title, LocalDate deadline) {
	}

	/**
	 * @param body       분류에 쓴 본문 글(근거 문장을 찾는다)
	 * @param subject    요약이 없을 때 쓴다
	 * @param myPostings 내 공고 목록의 id(LLM 에 보낸 것)
	 * @return 버릴 메일(채용 메일이 아니거나 공고를 모른다)이면 null
	 */
	static Outcome decide(MailAnswer answer, String body, String subject, Set<Long> myPostings) {
		if (answer == null || !answer.recruiting()) {
			return null;
		}
		Long postingId = myPostings.contains(answer.postingId()) ? answer.postingId() : null;
		NewPosting newPosting = null;
		if (postingId == null) {
			String company = strip(answer.company());
			String title = strip(answer.title());
			if (company.isEmpty() || title.isEmpty() || company.length() > MAX_COMPANY || title.length() > MAX_TITLE) {
				return null;
			}
			newPosting = new NewPosting(company, title, date(answer.deadline()));
		}
		ApplicationStatus proposal = proposal(answer.stage(), answer.result());
		LocalDate scheduleDate = (proposal != null) ? date(answer.scheduleDate()) : null;
		LocalTime scheduleTime = (scheduleDate != null) ? time(answer.scheduleTime()) : null;
		String summary = strip(answer.summary());
		if (summary.isEmpty() || summary.length() > MAX_SUMMARY) {
			summary = (subject.length() > MAX_SUMMARY) ? subject.substring(0, MAX_SUMMARY) : subject;
		}
		return new Outcome(postingId, newPosting, proposal, scheduleDate, scheduleTime, grounded(answer.quote(), body),
				summary);
	}

	/**
	 * 제안을 확인함에 둘 만한지. 지금 상태와 다르거나 새 일정이 있으면 둔다.
	 *
	 * @param current 지금 내 상태. 내 공고 행이 없으면 null
	 */
	static boolean worthReview(Outcome outcome, ApplicationStatus current) {
		return outcome.proposal() != null && (!outcome.proposal().equals(current) || outcome.scheduleDate() != null);
	}

	/** stage 와 result 가 모두 정해진 값이면 "지원함 · 그 단계 · 그 결과". */
	static ApplicationStatus proposal(String stage, String result) {
		Stage s = constant(Stage.class, stage);
		Result r = constant(Result.class, result);
		return (s != null && r != null) ? new ApplicationStatus(Decision.APPLIED, s, r) : null;
	}

	/** 근거 문장이 본문에 그대로 있을 때만(띄어쓰기는 가리지 않는다). */
	static String grounded(String quote, String body) {
		String q = strip(quote);
		if (q.isEmpty() || q.length() > MAX_QUOTE) {
			return null;
		}
		return squeeze(body).contains(squeeze(q)) ? q : null;
	}

	private static <E extends Enum<E>> E constant(Class<E> type, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static LocalDate date(String value) {
		try {
			return (value == null || value.isBlank()) ? null : LocalDate.parse(value.strip());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static LocalTime time(String value) {
		try {
			return (value == null || value.isBlank()) ? null : LocalTime.parse(value.strip());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static String squeeze(String text) {
		return text.replaceAll("\\s+", "");
	}

	private static String strip(String value) {
		return (value != null) ? value.strip() : "";
	}

}
