package com.readme.posting;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 공고 본체(postings). 그룹과 무관하게 공고 하나에 한 행이고, 여러 그룹이 같은 행을 가리킨다(설계 가정 2).
 *
 * <p>공고 내용 묶음(sections), 날짜 넣은 사람·시각은 여기서 다루지 않는다. 처음 만들 때는 DB 기본값이 들어가고,
 * 올린 묶음은 {@link Postings} 가 native update 로 넣는다.
 */
@Entity
@Table(name = "postings")
class Posting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 같은 공고 판별 키. 만들 수 없으면 null */
	private String postingKey;

	private String company;

	private String title;

	@Enumerated(EnumType.STRING)
	private DeadlineKind deadlineKind;

	private LocalDate deadlineDate;

	private String deadlineName;

	private String applyAt;

	private String sourceUrl;

	private Long createdBy;

	private Instant createdAt;

	private Instant updatedAt;

	protected Posting() {
	}

	Posting(PostingDraft draft, String postingKey, long createdBy, Instant now) {
		this.postingKey = postingKey;
		this.company = draft.company();
		this.title = draft.title();
		this.deadlineKind = draft.deadlineKind();
		this.deadlineDate = draft.deadlineDate();
		this.deadlineName = draft.deadlineName();
		this.applyAt = draft.applyAt();
		this.sourceUrl = draft.sourceUrl();
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	Long getId() {
		return this.id;
	}

	String postingKey() {
		return this.postingKey;
	}

	String company() {
		return this.company;
	}

	String title() {
		return this.title;
	}

}
