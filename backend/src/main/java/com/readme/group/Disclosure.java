package com.readme.group;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 내 지원 상태를 그룹에 얼마나 보일지(group_members.disclosure). 그룹마다 따로 고르고 기본은 APPLIED 다.
 */
public enum Disclosure {

	/** 관심만 */
	INTEREST,

	/** 지원 여부까지 */
	APPLIED,

	/** 단계·결과까지 */
	PROGRESS;

	/** 응답에 쓰는 값: "interest", "applied", "progress". */
	@JsonValue
	public String json() {
		return name().toLowerCase(Locale.ROOT);
	}

}
