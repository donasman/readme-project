package com.readme.posting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * LLM 에 보내는 내 경험카드 목록. 카드 우선순위(CP5)와 문항 매칭(QD7)이 함께 쓴다. card 패키지의 테이블을 읽기만 한다.
 */
@Component
class PromptCards {

	/** LLM 에 보내는 카드 수. 최근에 고친 카드부터 */
	// shortcut: 카드가 이보다 많으면 오래 고치지 않은 카드는 후보에서 빠진다, 카드가 많은 사용자가 생기면 규칙(CardMatching)으로 먼저 거른다
	static final int MAX = 60;

	/** 카드 칸 하나를 LLM 에 보내는 길이 */
	private static final int FIELD_CHARS = 300;

	private final JdbcClient jdbc;

	PromptCards(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** LLM 에 보낼 카드 한 장. text 는 "### 카드 id 3: 제목" 과 채워진 칸들이다. */
	record Card(long id, String text) {
	}

	/** 최근에 고친 카드부터 {@link #MAX}장. */
	List<Card> of(long userId) {
		Map<Long, List<String>> tags = this.jdbc.sql("""
				select t.card_id, t.tag from card_tags t join experience_cards c on c.id = t.card_id
				 where c.user_id = :userId order by t.card_id, t.tag
				""").param("userId", userId).query((rs, rowNum) -> Map.entry(rs.getLong("card_id"), rs.getString("tag")))
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		return this.jdbc.sql("""
				select id, title, experience, period, role, summary, situation, task, action, result, obstacle, learning
				  from experience_cards where user_id = :userId order by updated_at desc, id desc limit :limit
				""")
			.param("userId", userId)
			.param("limit", MAX)
			.query((rs, rowNum) -> {
				long id = rs.getLong("id");
				List<String> lines = new ArrayList<>();
				lines.add("### 카드 id " + id + ": " + rs.getString("title"));
				for (String[] field : new String[][] { { "경험", "experience" }, { "기간", "period" }, { "역할", "role" },
						{ "요약", "summary" }, { "상황", "situation" }, { "과제", "task" }, { "행동", "action" },
						{ "결과", "result" }, { "어려움", "obstacle" }, { "배운 점", "learning" } }) {
					String value = rs.getString(field[1]);
					if (value != null && !value.isBlank()) {
						lines.add("- " + field[0] + ": " + cut(value.strip().replaceAll("\\s+", " "), FIELD_CHARS));
					}
				}
				List<String> cardTags = tags.getOrDefault(id, List.of());
				if (!cardTags.isEmpty()) {
					lines.add("- 태그: " + String.join(", ", cardTags));
				}
				return new Card(id, String.join("\n", lines));
			})
			.list();
	}

	/** 앞뒤 빈칸을 떼고 max 글자(코드 포인트)에서 자른다. 비었으면 null. */
	static String cut(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.strip();
		return (text.codePointCount(0, text.length()) > max) ? text.substring(0, text.offsetByCodePoints(0, max)) : text;
	}

}
