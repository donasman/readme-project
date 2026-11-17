package com.readme.extraction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 카드 추출 작업(card_extraction_jobs). 상태는 QUEUED → RUNNING → DONE 이고, LLM 이 실패하면 FAILED 다(경험카드 설계 4.6).
 * done_parts 는 마친 조각 수다. 조각은 차례로 하므로 다음 조각은 done_parts + 1 이다.
 */
@Entity
@Table(name = "card_extraction_jobs")
class CardExtractionJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	private Long sourceId;

	/** QUEUED, RUNNING, DONE, FAILED */
	private String status;

	private int totalParts;

	private int doneParts;

	/** 화면에 보일 수 있는 실패 이유 */
	private String errorMessage;

	/** 이미 있는 카드와 같은 경험이라 건너뛴 카드의, 그 이미 있는 카드의 제목들(jsonb) */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> skippedTitles = new ArrayList<>();

	private Instant createdAt;

	private Instant startedAt;

	private Instant finishedAt;

	protected CardExtractionJob() {
	}

	CardExtractionJob(long userId, long sourceId, int totalParts, Instant now) {
		this.userId = userId;
		this.sourceId = sourceId;
		this.status = "QUEUED";
		this.totalParts = totalParts;
		this.createdAt = now;
	}

	Long getId() {
		return this.id;
	}

	long userId() {
		return this.userId;
	}

	long sourceId() {
		return this.sourceId;
	}

	String status() {
		return this.status;
	}

	int doneParts() {
		return this.doneParts;
	}

	int totalParts() {
		return this.totalParts;
	}

	List<String> skippedTitles() {
		return List.copyOf(this.skippedTitles);
	}

	/** 실패한 작업을 마친 조각 다음부터 다시 돌린다. */
	void queueAgain() {
		this.status = "QUEUED";
		this.errorMessage = null;
		this.finishedAt = null;
	}

	void start(Instant now) {
		this.status = "RUNNING";
		if (this.startedAt == null) {
			this.startedAt = now;
		}
	}

	void partDone(int part, List<String> skipped) {
		this.doneParts = part;
		List<String> titles = new ArrayList<>(this.skippedTitles);
		titles.addAll(skipped);
		this.skippedTitles = titles;
	}

	void done(Instant now) {
		this.status = "DONE";
		this.finishedAt = now;
	}

	void fail(String message, Instant now) {
		this.status = "FAILED";
		this.errorMessage = message;
		this.finishedAt = now;
	}

}
