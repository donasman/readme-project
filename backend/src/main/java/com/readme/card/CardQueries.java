package com.readme.card;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.card.CardRules.Content;

/**
 * 카드 화면에 내보내는 것을 읽는다. 카드는 쿼리 넷(카드, 태그, 문항 유형, 근거 숫자)으로 읽고, 검사 결과는 읽은 값으로 매긴다.
 * 모두 그 사람의 카드만 읽는다.
 *
 * <p>검사(CardRules.check)는 쿼리가 끝나 DB 연결을 돌려준 뒤에 한다. 검사는 글이 길수록 오래 걸려서, 행을 읽는 중에 하면
 * 그동안 연결(풀 5개)을 붙잡는다.
 */
@Component
class CardQueries {

	/** 카드 줄. 자료가 파일이면 그 이름이 붙는다(붙여넣은 글은 이름이 없다). */
	private static final String CARDS = """
			select c.id, c.title, c.period, c.org, c.role, c.summary, c.situation, c.task, c.action, c.result,
			       c.verified, c.visibility, c.source_id, c.extraction_notes::text as extraction_notes,
			       case when s.kind = 'FILE' then s.file_name end as source_name
			  from experience_cards c
			  left join card_sources s on s.id = c.source_id
			 where c.user_id = :userId
			""";

	private static final String CARDS_ORDER = " order by c.updated_at desc, c.id desc";

	private static final String TAGS = """
			select t.card_id, t.tag as value
			  from card_tags t join experience_cards c on c.id = t.card_id
			 where c.user_id = :userId
			""";

	private static final String TYPES = """
			select u.card_id, u.question_type as value
			  from card_usable_for u join experience_cards c on c.id = u.card_id
			 where c.user_id = :userId
			""";

	private static final String FACTS = """
			select f.card_id, f.body as value
			  from card_facts f join experience_cards c on c.id = f.card_id
			 where c.user_id = :userId
			""";

	/** 태그 순서: 대소문자를 가리지 않는 이름순(영문이 한글보다 앞). DB 의 정렬 규칙(collation)에 기대지 않으려고 여기서 정렬한다. */
	private static final Comparator<String> TAG_ORDER = Comparator.comparing((String tag) -> tag.toLowerCase(Locale.ROOT))
		.thenComparing(Comparator.naturalOrder());

	private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	CardQueries(JdbcClient jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	/** 내 카드 목록. 최근에 고친 것이 앞이다. 거르는 값이 null 이면 거르지 않는다. */
	List<CardResponse> list(long userId, CardFilter filter) {
		StringBuilder sql = new StringBuilder(CARDS);
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("userId", userId);
		if (filter.tag() != null) {
			sql.append(" and exists (select 1 from card_tags t where t.card_id = c.id and t.tag = :tag)");
			params.put("tag", filter.tag());
		}
		if (filter.type() != null) {
			sql.append(" and exists (select 1 from card_usable_for u where u.card_id = c.id and u.question_type = :type)");
			params.put("type", filter.type());
		}
		if (filter.verified() != null) {
			sql.append(" and c.verified = :verified");
			params.put("verified", filter.verified());
		}
		if (filter.jobId() != null) {
			sql.append(" and c.job_id = :jobId");
			params.put("jobId", filter.jobId());
		}
		if (filter.q() != null) {
			// 대소문자를 가리지 않는 포함 검색. % 와 _ 를 글자 그대로 보려고 like 대신 strpos 를 쓴다
			sql.append("""
					 and (strpos(lower(concat_ws(chr(10), c.title, c.period, c.org, c.role, c.summary,
					                             c.situation, c.task, c.action, c.result)), lower(:q)) > 0
					      or exists (select 1 from card_tags t where t.card_id = c.id and strpos(lower(t.tag), lower(:q)) > 0)
					      or exists (select 1 from card_facts f where f.card_id = c.id and strpos(lower(f.body), lower(:q)) > 0))
					""");
			params.put("q", filter.q());
		}
		Children children = children(userId, null);
		return this.jdbc.sql(sql + CARDS_ORDER)
			.params(params)
			.query((rs, rowNum) -> card(rs, children))
			.list()
			.stream()
			.map(CardQueries::checked)
			.toList();
	}

	/** 내 카드 한 장. 없거나 남의 카드면 비어 있다. */
	Optional<CardResponse> find(long userId, long cardId) {
		Children children = children(userId, cardId);
		return this.jdbc.sql(CARDS + " and c.id = :cardId")
			.param("userId", userId)
			.param("cardId", cardId)
			.query((rs, rowNum) -> card(rs, children))
			.optional()
			.map(CardQueries::checked);
	}

	/** 내 카드에 쓰인 태그와 카드 수. 많이 쓰인 것이 앞, 같으면 이름순이다. */
	List<CardTagResponse> tags(long userId) {
		return this.jdbc.sql("""
				select t.tag, count(*) as count
				  from card_tags t join experience_cards c on c.id = t.card_id
				 where c.user_id = :userId
				 group by t.tag
				""")
			.param("userId", userId)
			.query((rs, rowNum) -> new CardTagResponse(rs.getString("tag"), rs.getLong("count")))
			.list()
			.stream()
			.sorted(Comparator.comparingLong(CardTagResponse::count).reversed()
				.thenComparing(CardTagResponse::name, TAG_ORDER))
			.toList();
	}

	private Children children(long userId, Long cardId) {
		String oneCard = (cardId != null) ? " and c.id = :cardId" : "";
		Map<Long, List<String>> tags = values(TAGS + oneCard, userId, cardId);
		tags.values().forEach((list) -> list.sort(TAG_ORDER));
		Map<Long, List<String>> types = values(TYPES + oneCard, userId, cardId);
		types.values().forEach((list) -> list.sort(Comparator.comparingInt(CardRules.QUESTION_TYPES::indexOf)));
		return new Children(tags, types, values(FACTS + oneCard + " order by f.card_id, f.seq", userId, cardId));
	}

	private Map<Long, List<String>> values(String sql, long userId, Long cardId) {
		JdbcClient.StatementSpec statement = this.jdbc.sql(sql).param("userId", userId);
		if (cardId != null) {
			statement = statement.param("cardId", cardId);
		}
		return statement.query((rs, rowNum) -> Map.entry(rs.getLong("card_id"), rs.getString("value")))
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(ArrayList::new))));
	}

	/** 행 하나를 카드로. 검사 결과는 비워 두고 {@link #checked} 가 매긴다. */
	private CardResponse card(ResultSet rs, Children children) throws SQLException {
		long id = rs.getLong("id");
		String title = rs.getString("title");
		String period = rs.getString("period");
		String summary = rs.getString("summary");
		Star star = new Star(rs.getString("situation"), rs.getString("task"), rs.getString("action"),
				rs.getString("result"));
		List<String> tags = children.tags().getOrDefault(id, List.of());
		List<String> types = children.types().getOrDefault(id, List.of());
		List<String> facts = children.facts().getOrDefault(id, List.of());
		String notes = rs.getString("extraction_notes");
		return new CardResponse(id, title, period, rs.getString("org"), rs.getString("role"), tags,
				(summary != null) ? summary : "", types, rs.getBoolean("verified"), star, facts,
				rs.getString("visibility"), rs.getObject("source_id", Long.class), rs.getString("source_name"),
				(notes != null) ? this.jsonMapper.readValue(notes, STRINGS) : List.of(), List.of());
	}

	/** 검사 결과를 매긴 카드. 빈 요약("")은 검사에서 비어 있는 것과 같다. */
	private static CardResponse checked(CardResponse card) {
		List<CardIssue> issues = CardRules.check(new Content(card.title(), card.period(), card.summary(), card.tags(),
				card.usableFor(), card.star(), card.facts()));
		return new CardResponse(card.id(), card.title(), card.period(), card.org(), card.role(), card.tags(),
				card.summary(), card.usableFor(), card.verified(), card.star(), card.facts(), card.visibility(),
				card.sourceId(), card.sourceName(), card.extractionNotes(), issues);
	}

	/** 목록의 거르기. 태그는 대표 표기, 문항 유형은 6종 중 하나다. */
	record CardFilter(String tag, String type, Boolean verified, Long jobId, String q) {
	}

	private record Children(Map<Long, List<String>> tags, Map<Long, List<String>> types, Map<Long, List<String>> facts) {
	}

}
