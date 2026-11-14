package com.readme.posting;

import java.time.LocalDate;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 마감. 프런트의 Deadline 모양: { "kind": "date", "date": "2026-11-16" }, { "kind": "always" }, { "kind": "unknown" }.
 * 그룹 공고와 내 공고가 함께 쓴다.
 *
 * @param kind DB 의 deadline_kind(DATE, ALWAYS, UNKNOWN)를 소문자로
 */
public record DeadlineResponse(String kind, @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate date) {

	public static DeadlineResponse of(String deadlineKind, LocalDate deadlineDate) {
		return new DeadlineResponse(deadlineKind.toLowerCase(Locale.ROOT), deadlineDate);
	}

}
