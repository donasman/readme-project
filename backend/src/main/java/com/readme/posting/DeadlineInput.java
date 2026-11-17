package com.readme.posting;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;

/**
 * 마감. 프런트의 Deadline 모양 그대로: { "kind": "date", "date": "2026-11-16" }, { "kind": "always" }, { "kind": "unknown" }.
 * 올리기·내 공고 넣기 본문의 deadline 이고, 그룹 공고의 공고 고치기 본문의 deadline, PUT …/deadline(날짜 넣기)의 본문 자체다.
 */
public record DeadlineInput(@NotNull DeadlineKind kind, LocalDate date) {

	/** 종류와 날짜의 짝이 맞는지: 날짜는 "date"일 때만, 꼭 있다. */
	public boolean isConsistent() {
		return (this.kind == DeadlineKind.DATE) == (this.date != null);
	}

	/** 짝이 맞으면 그대로 돌려주고, 아니면 400 INVALID_INPUT. */
	public DeadlineInput requireConsistent() {
		if (!isConsistent()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "마감일은 마감이 \"date\"일 때만, 꼭 넣어 주세요");
		}
		return this;
	}

}
