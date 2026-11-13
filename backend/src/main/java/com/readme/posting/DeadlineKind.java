package com.readme.posting;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 마감의 종류(postings.deadline_kind). 날짜는 DATE 일 때만 있다(테이블 제약).
 */
public enum DeadlineKind {

	/** 마감일이 있다 */
	DATE,

	/** 상시·수시 채용 */
	ALWAYS,

	/** 마감일을 읽지 못했다. 멤버가 "날짜 넣기"로 한 번 채운다 */
	UNKNOWN;

	/** 요청·응답에 쓰는 값: "date", "always", "unknown". */
	@JsonValue
	public String json() {
		return name().toLowerCase(Locale.ROOT);
	}

}
