package com.readme.card;

import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.Size;

/**
 * 카드의 이야기 칸: STAR 네 칸과 어려움(SOAR 의 O), 배운 점(STAR-L·CARL 의 L)(경험카드 설계 4.8, 2026-11-17). 프런트의 Star 모양이다.
 * 빈 칸은 빈 글자다(null 로 받아도 빈 글자로 바꾼다). 어려움과 배운 점은 선택이다.
 * 받을 때 한 칸은 2,000자까지다(CardRequests 의 설명). 칸(text)에는 길이가 없다.
 */
record Star(@Size(max = 2000) String situation, @Size(max = 2000) String task, @Size(max = 2000) String action,
		@Size(max = 2000) String result, @Size(max = 2000) String obstacle, @Size(max = 2000) String learning) {

	static final Star EMPTY = new Star("", "", "", "", "", "");

	Star {
		situation = Objects.requireNonNullElse(situation, "");
		task = Objects.requireNonNullElse(task, "");
		action = Objects.requireNonNullElse(action, "");
		result = Objects.requireNonNullElse(result, "");
		obstacle = Objects.requireNonNullElse(obstacle, "");
		learning = Objects.requireNonNullElse(learning, "");
	}

	/** 여섯 칸을 이 record 의 순서로. 숫자 검사와 다듬기가 모두 돈다. 앞의 넷이 STAR 다(빈 칸 경고는 넷만 본다). */
	List<String> parts() {
		return List.of(this.situation, this.task, this.action, this.result, this.obstacle, this.learning);
	}

	/** parts() 순서의 여섯 글로. */
	static Star of(List<String> parts) {
		return new Star(parts.get(0), parts.get(1), parts.get(2), parts.get(3), parts.get(4), parts.get(5));
	}

}
