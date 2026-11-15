package com.readme.card;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.type.SqlTypes;

import com.readme.card.CardRequests.CardInput;

/**
 * 경험카드(experience_cards). 태그, 문항 유형, 근거 숫자는 카드별 행으로 따로 둔다(설계 가정 C1).
 *
 * <p>자료(source_id), 추출 작업(job_id), 조각 번호(source_part)는 자료에서 뽑은 카드에만 있다. 직접 만든 카드에는 없다.
 */
@Entity
@Table(name = "experience_cards")
class ExperienceCard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	private String title;

	/** "2025-03 ~ 2025-06" 글자 그대로 */
	private String period;

	private String org;

	private String role;

	private String summary;

	private String situation;

	private String task;

	private String action;

	private String result;

	private boolean verified;

	private Instant verifiedAt;

	/** 지금은 PRIVATE 하나다(설계 가정 C4) */
	private String visibility;

	private Long sourceId;

	private Long jobId;

	/** 카드가 나온 자료 조각(1부터) */
	private Integer sourcePart;

	/** 추출 때 생긴 안내 목록(jsonb). 카드를 확인하면 비운다 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> extractionNotes;

	private Instant createdAt;

	private Instant updatedAt;

	/** 키워드 사전의 대표 표기. 사전에 없으면 친 그대로 */
	@ElementCollection
	@CollectionTable(name = "card_tags", joinColumns = @JoinColumn(name = "card_id"))
	@Column(name = "tag")
	private Set<String> tags = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "card_usable_for", joinColumns = @JoinColumn(name = "card_id"))
	@Column(name = "question_type")
	private Set<String> usableFor = new HashSet<>();

	/** 화면에 보이는 순서대로. seq 는 1부터 */
	@ElementCollection
	@CollectionTable(name = "card_facts", joinColumns = @JoinColumn(name = "card_id"))
	@OrderColumn(name = "seq")
	@ListIndexBase(1)
	@Column(name = "body")
	private List<String> facts = new ArrayList<>();

	protected ExperienceCard() {
	}

	/** 직접 만든 카드. 자료가 없고, 확인 전이다. */
	ExperienceCard(long userId, CardInput card, Instant now) {
		this.userId = userId;
		this.visibility = "PRIVATE";
		this.createdAt = now;
		edit(card, now);
	}

	/** 자료에서 뽑은 카드(설계 4.6). 확인 전이고, 추출 때 생긴 안내를 단다. */
	ExperienceCard(long userId, CardInput card, long sourceId, long jobId, int sourcePart, List<String> notes,
			Instant now) {
		this(userId, card, now);
		this.sourceId = sourceId;
		this.jobId = jobId;
		this.sourcePart = sourcePart;
		this.extractionNotes = notes.isEmpty() ? null : List.copyOf(notes);
	}

	Long getId() {
		return this.id;
	}

	boolean isOwnedBy(long userId) {
		return this.userId == userId;
	}

	Star star() {
		return new Star(this.situation, this.task, this.action, this.result);
	}

	List<String> facts() {
		return List.copyOf(this.facts);
	}

	/** 모든 칸을 바꾼다. 확인 여부와 자료는 그대로 둔다. */
	void edit(CardInput card, Instant now) {
		this.title = card.title();
		this.period = card.period();
		this.org = card.org();
		this.role = card.role();
		this.summary = card.summary();
		this.situation = emptyToNull(card.star().situation());
		this.task = emptyToNull(card.star().task());
		this.action = emptyToNull(card.star().action());
		this.result = emptyToNull(card.star().result());
		this.tags.clear();
		this.tags.addAll(card.tags());
		this.usableFor.clear();
		this.usableFor.addAll(card.usableFor());
		this.facts.clear();
		this.facts.addAll(card.facts());
		this.updatedAt = now;
	}

	/** 카드 확인: 근거 숫자를 더하고, 확인한 것으로 두고, 추출 안내를 비운다. */
	void verify(List<String> moreFacts, Instant now) {
		this.facts.addAll(moreFacts);
		this.verified = true;
		this.verifiedAt = now;
		this.extractionNotes = null;
		this.updatedAt = now;
	}

	private static String emptyToNull(String text) {
		return text.isEmpty() ? null : text;
	}

}
