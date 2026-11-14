package com.readme.card;

import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.Size;

/**
 * STAR 네 칸. 프런트의 Star 모양이다. 빈 칸은 빈 글자다(null 로 받아도 빈 글자로 바꾼다).
 * 받을 때 한 칸은 2,000자까지다(CardRequests 의 설명). 칸(text)에는 길이가 없다.
 */
record Star(@Size(max = 2000) String situation, @Size(max = 2000) String task, @Size(max = 2000) String action,
		@Size(max = 2000) String result) {

	static final Star EMPTY = new Star("", "", "", "");

	Star {
		situation = Objects.requireNonNullElse(situation, "");
		task = Objects.requireNonNullElse(task, "");
		action = Objects.requireNonNullElse(action, "");
		result = Objects.requireNonNullElse(result, "");
	}

	/** 네 칸을 화면 순서(상황, 과제, 행동, 결과)로. */
	List<String> parts() {
		return List.of(this.situation, this.task, this.action, this.result);
	}

}
