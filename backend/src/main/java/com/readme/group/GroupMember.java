package com.readme.group;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 그룹 × 사람(group_members). 역할과 이 그룹에 정한 공개 범위가 있다. 그룹장은 그룹당 한 명이다(부분 유일 인덱스).
 */
@Entity
@Table(name = "group_members")
@IdClass(GroupMember.Key.class)
class GroupMember {

	@Id
	private Long groupId;

	@Id
	private Long userId;

	@Enumerated(EnumType.STRING)
	private Role role;

	@Enumerated(EnumType.STRING)
	private Disclosure disclosure;

	private Instant joinedAt;

	protected GroupMember() {
	}

	/** 그룹을 만든 사람. 공개 범위는 기본값(지원 여부까지)이다. */
	static GroupMember leader(long groupId, long userId, Instant now) {
		GroupMember member = new GroupMember();
		member.groupId = groupId;
		member.userId = userId;
		member.role = Role.LEADER;
		member.disclosure = Disclosure.APPLIED;
		member.joinedAt = now;
		return member;
	}

	void changeDisclosure(Disclosure disclosure) {
		this.disclosure = disclosure;
	}

	enum Role {
		LEADER, MEMBER
	}

	record Key(Long groupId, Long userId) implements Serializable {
	}

}
