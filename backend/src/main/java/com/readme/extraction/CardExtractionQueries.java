package com.readme.extraction;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 카드 뽑기의 읽기. 카드와 태그(card 패키지의 테이블)는 읽기만 한다. */
@Component
class CardExtractionQueries {

	private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	CardExtractionQueries(JdbcClient jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	/** @param name 파일 이름. 붙여넣은 글이면 null */
	record Source(String name, String body) {
	}

	/** 내 자료. 남의 자료면 비어 있다. */
	Optional<Source> mySource(long userId, long sourceId) {
		return this.jdbc.sql("select file_name, body from card_sources where id = :id and user_id = :userId")
			.param("id", sourceId)
			.param("userId", userId)
			.query((rs, rowNum) -> new Source(rs.getString("file_name"), rs.getString("body")))
			.optional();
	}

	Optional<Source> source(long sourceId) {
		return this.jdbc.sql("select file_name, body from card_sources where id = :id")
			.param("id", sourceId)
			.query((rs, rowNum) -> new Source(rs.getString("file_name"), rs.getString("body")))
			.optional();
	}

	/** 내 카드의 제목. 먼저 만든 것이 앞이다. */
	List<String> cardTitles(long userId) {
		return this.jdbc.sql("select title from experience_cards where user_id = :userId order by created_at, id")
			.param("userId", userId)
			.query(String.class)
			.list();
	}

	/** 내 카드에 쓰인 태그. 많이 쓰인 것이 앞이다. */
	List<String> cardTags(long userId) {
		return this.jdbc.sql("""
				select t.tag from card_tags t join experience_cards c on c.id = t.card_id
				 where c.user_id = :userId group by t.tag order by count(*) desc, t.tag
				""").param("userId", userId).query(String.class).list();
	}

	/** 내 작업. 남의 작업이거나 없으면 비어 있다. */
	Optional<ExtractionJobResponse> job(long userId, long jobId) {
		return this.jdbc.sql("""
				select j.id, j.source_id, j.status, j.done_parts, j.total_parts, j.skipped_titles::text as skipped,
				       j.error_message, (select count(*) from experience_cards c where c.job_id = j.id) as cards
				  from card_extraction_jobs j
				 where j.id = :jobId and j.user_id = :userId
				""").param("jobId", jobId).param("userId", userId).query((rs, rowNum) -> {
			String status = rs.getString("status");
			int cards = rs.getInt("cards");
			return new ExtractionJobResponse(rs.getLong("id"), rs.getLong("source_id"), status.toLowerCase(Locale.ROOT),
					rs.getInt("done_parts"), rs.getInt("total_parts"), cards, status.equals("DONE") ? cards : 0,
					this.jsonMapper.readValue(rs.getString("skipped"), STRINGS),
					status.equals("FAILED") ? rs.getString("error_message") : null);
		}).optional();
	}

}
