package com.readme.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * 기본 프로필(profiles 와 여러 줄 넷). 행은 ProfileRepository.insertIfAbsent 로 만들고, 고치기는 이 엔티티로 한다.
 */
@Entity
@Table(name = "profiles")
class Profile {

	@Id
	private Long userId;

	private String name;

	private LocalDate birthDate;

	private Instant updatedAt;

	@ElementCollection
	@CollectionTable(name = "profile_educations", joinColumns = @JoinColumn(name = "user_id"))
	@OrderColumn(name = "seq")
	private List<Education> educations = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "profile_certificates", joinColumns = @JoinColumn(name = "user_id"))
	@OrderColumn(name = "seq")
	private List<Certificate> certificates = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "profile_languages", joinColumns = @JoinColumn(name = "user_id"))
	@OrderColumn(name = "seq")
	private List<Language> languages = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "profile_careers", joinColumns = @JoinColumn(name = "user_id"))
	@OrderColumn(name = "seq")
	private List<Career> careers = new ArrayList<>();

	protected Profile() {
	}

	/** 모든 칸을 이 값으로 바꾼다. */
	void replace(ProfileInput input, Instant now) {
		this.name = input.name();
		this.birthDate = input.birthDate();
		replaceAll(this.educations, input.educations());
		replaceAll(this.certificates, input.certificates());
		replaceAll(this.languages, input.languages());
		replaceAll(this.careers, input.careers());
		this.updatedAt = now;
	}

	/** 빈 칸만 채운다. 목록은 한 줄도 없을 때만. 바뀐 것이 있을 때만 고친 때를 바꾼다. */
	void fillBlanks(ProfileInput input, Instant now) {
		boolean changed = false;
		if (this.name == null && input.name() != null) {
			this.name = input.name();
			changed = true;
		}
		if (this.birthDate == null && input.birthDate() != null) {
			this.birthDate = input.birthDate();
			changed = true;
		}
		changed |= fillIfEmpty(this.educations, input.educations());
		changed |= fillIfEmpty(this.certificates, input.certificates());
		changed |= fillIfEmpty(this.languages, input.languages());
		changed |= fillIfEmpty(this.careers, input.careers());
		if (changed) {
			this.updatedAt = now;
		}
	}

	private static <T> boolean fillIfEmpty(List<T> rows, List<T> values) {
		if (!rows.isEmpty() || values.isEmpty()) {
			return false;
		}
		rows.addAll(values);
		return true;
	}

	private static <T> void replaceAll(List<T> rows, List<T> values) {
		rows.clear();
		rows.addAll(values);
	}

	@Embeddable
	record Education(String school, String major, String graduation, BigDecimal gpa, BigDecimal gpaScale) {
	}

	@Embeddable
	record Certificate(String name, String acquired) {
	}

	@Embeddable
	record Language(String test, String score, String taken) {
	}

	/** @param endMonth null 이면 재직 중 */
	@Embeddable
	record Career(String company, @Column(name = "start_month") String startMonth,
			@Column(name = "end_month") String endMonth, String role) {
	}

	/** 다듬은 프로필 값. 빈 칸은 null 이다. */
	record ProfileInput(String name, LocalDate birthDate, List<Education> educations,
			List<Certificate> certificates, List<Language> languages, List<Career> careers) {
	}

}
