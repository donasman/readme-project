package com.readme.inboundmail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.readme.posting.ApplicationStatus;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 받은 메일 한 통과 읽어 낸 칸(inbound_mails, 메일 설계 3절). 상태는 WAITING → READING → READ 이고, Gmail 전달 확인 메일은
 * FORWARDING 이다. 행을 만들고 상태를 옮기는 것은 리포지토리의 조건이 붙은 native 쓰기로, 분류가 끝난 칸은 {@link #read} 로 넣는다.
 */
@Entity
@Table(name = "inbound_mails")
class InboundMail {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String messageKey;

	private String sender;

	private String subject;

	private Instant receivedAt;

	/** WAITING, READING, READ, FORWARDING */
	private String status;

	private Instant readingStartedAt;

	/** 분류 전의 본문 글. 분류하면 null */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private String body;

	private String code;

	private Long postingId;

	private boolean added;

	private boolean reverted;

	/** PENDING, APPLIED, IGNORED. 제안이 없으면 null */
	private String review;

	@Enumerated(EnumType.STRING)
	private Stage stage;

	@Enumerated(EnumType.STRING)
	private Result result;

	private LocalDate scheduleDate;

	private LocalTime scheduleTime;

	private String quote;

	private String summary;

	protected InboundMail() {
	}

	/**
	 * 분류가 끝났다. 본문 글을 지운다.
	 *
	 * @param proposal 확인 필요로 둘 지원 상태. 없으면 null(새 공고만 넣었다)
	 */
	void read(long postingId, boolean added, ApplicationStatus proposal, LocalDate scheduleDate, LocalTime scheduleTime,
			String quote, String summary) {
		this.status = "READ";
		this.readingStartedAt = null;
		this.body = null;
		this.postingId = postingId;
		this.added = added;
		this.review = (proposal != null) ? "PENDING" : null;
		this.stage = (proposal != null) ? proposal.stage() : null;
		this.result = (proposal != null) ? proposal.result() : null;
		this.scheduleDate = (proposal != null) ? scheduleDate : null;
		this.scheduleTime = (proposal != null && scheduleDate != null) ? scheduleTime : null;
		this.quote = quote;
		this.summary = summary;
	}

	long userId() {
		return this.userId;
	}

	String status() {
		return this.status;
	}

	String body() {
		return this.body;
	}

	String sender() {
		return this.sender;
	}

	String subject() {
		return this.subject;
	}

	Instant receivedAt() {
		return this.receivedAt;
	}

	Long postingId() {
		return this.postingId;
	}

	/** 확인 필요의 지원 상태. 제안이 없으면 null */
	ApplicationStatus proposal() {
		return (this.stage != null) ? new ApplicationStatus(ApplicationStatus.Decision.APPLIED, this.stage, this.result) : null;
	}

	LocalDate scheduleDate() {
		return this.scheduleDate;
	}

	LocalTime scheduleTime() {
		return this.scheduleTime;
	}

}
