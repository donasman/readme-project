package com.readme.card;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.keyword.KeywordDictionary;

/**
 * 카드 API 가 받는 본문. 길이 제한은 테이블 칸의 길이와 같다. STAR 네 칸(text)은 길이 제한이 없다.
 */
final class CardRequests {

	private CardRequests() {
	}

	/**
	 * POST /api/cards, PUT /api/cards/{id} 의 본문. 프런트의 CardInput 모양이다. PUT 은 모든 칸을 이 값으로 바꾼다.
	 * 제목만 꼭 넣는다. 빈 요약·태그·문항 유형은 저장하고 검사(issues)가 오류로 알린다.
	 */
	record CardRequest(@NotBlank @Size(max = 200) String title, @Size(max = 50) String period,
			@Size(max = 200) String org, @Size(max = 200) String role, @Size(max = 200) String summary,
			List<@NotBlank @Size(max = 100) String> tags, List<String> usableFor, Star star,
			List<@NotBlank @Size(max = 500) String> facts) {

		/**
		 * 저장할 값. 앞뒤 공백을 지우고 빈 글자는 넣지 않은 것으로 본다. 태그는 대표 표기로 바꾸고(사전에 없으면 친 그대로),
		 * 문항 유형은 6종으로 읽는다(기술역량 → 직무역량). 읽을 수 없는 문항 유형이 있으면 400 INVALID_INPUT.
		 */
		CardInput toInput() {
			KeywordDictionary dictionary = KeywordDictionary.standard();
			LinkedHashSet<String> canonicalTags = new LinkedHashSet<>();
			orEmpty(this.tags).forEach((tag) -> canonicalTags.add(dictionary.normalize(tag)));
			LinkedHashSet<String> types = new LinkedHashSet<>();
			for (String value : orEmpty(this.usableFor)) {
				String type = CardRules.normalizeType(value);
				if (type == null) {
					throw new ApiException(CommonErrorCode.INVALID_INPUT,
							"쓸 문항 유형은 " + String.join(" · ", CardRules.QUESTION_TYPES) + " 중에서 골라 주세요");
				}
				types.add(type);
			}
			Star given = Objects.requireNonNullElse(this.star, Star.EMPTY);
			Star stripped = new Star(given.situation().strip(), given.task().strip(), given.action().strip(),
					given.result().strip());
			return new CardInput(this.title.strip(), blankToNull(this.period), blankToNull(this.org),
					blankToNull(this.role), blankToNull(this.summary), stripped, List.copyOf(canonicalTags),
					List.copyOf(types), orEmpty(this.facts).stream().map(String::strip).toList());
		}

		private static <T> List<T> orEmpty(List<T> values) {
			return (values != null) ? values : List.of();
		}

		private static String blankToNull(String text) {
			return (text == null || text.isBlank()) ? null : text.strip();
		}

	}

	/** 다듬은 카드 값. 태그는 대표 표기, 문항 유형은 6종 중 하나이고 겹치지 않는다. 빈 칸은 null, 빈 STAR 칸은 빈 글자다. */
	record CardInput(String title, String period, String org, String role, String summary, Star star, List<String> tags,
			List<String> usableFor, List<String> facts) {
	}

}
