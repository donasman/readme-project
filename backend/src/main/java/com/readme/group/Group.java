package com.readme.group;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 그룹(groups).
 */
@Entity
@Table(name = "groups")
class Group {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	/** 헤더 동그라미 안의 글자 */
	private String mark;

	private Long createdBy;

	private Instant createdAt;

	private Instant updatedAt;

	protected Group() {
	}

	Group(String name, String mark, long createdBy, Instant now) {
		this.name = name;
		this.mark = mark;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	Long getId() {
		return this.id;
	}

}
