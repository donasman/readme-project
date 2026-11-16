package com.readme.inboundmail;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.common.config.TimeConfig;
import com.readme.posting.ApplicationStatus;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/** 메일 확인함을 읽는다(메일 설계 4.4). 회사명과 공고명은 공고 본체의 지금 값, 바뀌기 전 상태는 지금 내 공고의 상태다. */
@Component
class MailboxQueries {

	private final JdbcClient jdbc;

	MailboxQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 프런트의 Mailbox 모양에 Gmail 전달 확인 메일과 기다리는 메일 수가 더해진다. 모두 받은 때의 역순이다.
	 *
	 * @param waiting 분류를 기다리는 메일 수
	 */
	record MailboxResponse(List<PendingMail> pending, List<AddedMail> added, List<ForwardingMail> forwarding,
			int waiting) {
	}

	/** 지원 상태(프런트의 MyState). 단계와 결과는 지원함일 때만 있다. */
	record MyState(Decision decision, Stage stage, Result result) {

		static MyState of(ApplicationStatus status) {
			return (status != null) ? new MyState(status.decision(), status.stage(), status.result()) : null;
		}

	}

	/** @param time "HH:mm". 없으면 null */
	record Schedule(Stage stage, LocalDate date, String time) {
	}

	/**
	 * 확인 필요 하나.
	 *
	 * @param before 지금 내 상태. 내 공고 행이 없으면 null
	 * @param after  반영하면 바뀔 상태
	 * @param quote  근거 문장. 없으면 null
	 */
	record PendingMail(long id, String from, OffsetDateTime receivedAt, String subject, String summary, long postingId,
			String company, String title, MyState before, MyState after, Schedule schedule, String quote) {
	}

	/** 메일에서 읽어 내 공고에 새로 넣은 것. 되돌린 것은 싣지 않는다. */
	record AddedMail(long id, String from, OffsetDateTime receivedAt, String subject, long postingId, String company,
			String title) {
	}

	/** Gmail 전달 확인 메일. code 는 제목에서 꺼낸 확인 코드이고, 못 꺼냈으면 null */
	record ForwardingMail(long id, OffsetDateTime receivedAt, String subject, String code) {
	}

	MailboxResponse mailbox(long userId) {
		List<PendingMail> pending = this.jdbc.sql("""
				select m.id, m.sender, m.received_at, m.subject, m.summary, m.posting_id, p.company, p.title,
				       up.decision, up.stage as now_stage, up.result as now_result, m.stage, m.result,
				       m.schedule_date, to_char(m.schedule_time, 'HH24:MI') as schedule_time, m.quote
				  from inbound_mails m
				  join postings p on p.id = m.posting_id
				  left join user_postings up on up.user_id = m.user_id and up.posting_id = m.posting_id
				 where m.user_id = :userId and m.status = 'READ' and m.review = 'PENDING'
				 order by m.received_at desc, m.id desc
				""").param("userId", userId).query((rs, rowNum) -> {
					Stage stage = Stage.valueOf(rs.getString("stage"));
					LocalDate date = rs.getObject("schedule_date", LocalDate.class);
					return new PendingMail(rs.getLong("id"), rs.getString("sender"), seoulTime(rs, "received_at"),
							rs.getString("subject"), rs.getString("summary"), rs.getLong("posting_id"),
							rs.getString("company"), rs.getString("title"),
							MyState.of(ApplicationStatus.of(rs.getString("decision"), rs.getString("now_stage"),
									rs.getString("now_result"))),
							new MyState(Decision.APPLIED, stage, Result.valueOf(rs.getString("result"))),
							(date != null) ? new Schedule(stage, date, rs.getString("schedule_time")) : null,
							rs.getString("quote"));
				}).list();
		List<AddedMail> added = this.jdbc.sql("""
				select m.id, m.sender, m.received_at, m.subject, m.posting_id, p.company, p.title
				  from inbound_mails m
				  join postings p on p.id = m.posting_id
				 where m.user_id = :userId and m.status = 'READ' and m.added and not m.reverted
				 order by m.received_at desc, m.id desc
				""")
			.param("userId", userId)
			.query((rs, rowNum) -> new AddedMail(rs.getLong("id"), rs.getString("sender"), seoulTime(rs, "received_at"),
					rs.getString("subject"), rs.getLong("posting_id"), rs.getString("company"), rs.getString("title")))
			.list();
		List<ForwardingMail> forwarding = this.jdbc.sql("""
				select id, received_at, subject, code from inbound_mails
				 where user_id = :userId and status = 'FORWARDING'
				 order by received_at desc, id desc
				""")
			.param("userId", userId)
			.query((rs, rowNum) -> new ForwardingMail(rs.getLong("id"), seoulTime(rs, "received_at"),
					rs.getString("subject"), rs.getString("code")))
			.list();
		int waiting = this.jdbc.sql("""
				select count(*) from inbound_mails where user_id = :userId and status in ('WAITING', 'READING')
				""").param("userId", userId).query(Integer.class).single();
		return new MailboxResponse(pending, added, forwarding, waiting);
	}

	private static OffsetDateTime seoulTime(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).atZoneSameInstant(TimeConfig.SEOUL).toOffsetDateTime();
	}

}
