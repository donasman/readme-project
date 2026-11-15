package com.readme.extraction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.common.config.TimeConfig;

@Component
class CardSourceQueries {

	// 자료마다 그 자료에서 뽑은 카드 수와 가장 최근 추출 작업 하나를 붙인다. 카드 수는 experience_cards 를 읽기만 한다.
	// shortcut: 조각 수를 세려고 본문을 모두 읽는다, 자료가 많아지거나 본문이 커지면 조각 수를 칸으로 둔다(마이그레이션)
	private static final String SOURCES = """
			select s.id, s.kind, s.file_name, s.char_count, s.body, s.created_at,
			       (select count(*) from experience_cards c
			         where c.user_id = s.user_id and c.source_id = s.id) as card_count,
			       j.id as job_id, j.status, j.done_parts, j.total_parts
			  from card_sources s
			  left join lateral (
			       select j.id, j.status, j.done_parts, j.total_parts
			         from card_extraction_jobs j
			        where j.user_id = s.user_id and j.source_id = s.id
			        order by j.created_at desc, j.id desc
			        limit 1) j on true
			 where s.user_id = :userId
			""";

	private final JdbcClient jdbc;

	CardSourceQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 내 자료. 새 것이 앞이다. */
	List<CardSourceResponse> findMine(long userId) {
		return this.jdbc.sql(SOURCES + " order by s.created_at desc, s.id desc")
			.param("userId", userId)
			.query((rs, rowNum) -> source(rs))
			.list();
	}

	/** 내 자료 하나. 내 것이 아니면 비어 있다. */
	Optional<CardSourceResponse> findMine(long userId, long sourceId) {
		return this.jdbc.sql(SOURCES + " and s.id = :sourceId")
			.param("userId", userId)
			.param("sourceId", sourceId)
			.query((rs, rowNum) -> source(rs))
			.optional();
	}

	private static CardSourceResponse source(ResultSet rs) throws SQLException {
		long jobId = rs.getLong("job_id");
		CardSourceResponse.LatestJob job = rs.wasNull() ? null
				: new CardSourceResponse.LatestJob(jobId, rs.getString("status").toLowerCase(Locale.ROOT),
						rs.getInt("done_parts"), rs.getInt("total_parts"));
		// 추출 작업이 없고 카드도 없으면 아직 뽑지 않은 자료다(null). 뽑았는데 카드를 모두 지웠으면 0 이다
		int cards = rs.getInt("card_count");
		Integer cardCount = (job == null && cards == 0) ? null : cards;
		return new CardSourceResponse(rs.getLong("id"), rs.getString("file_name"),
				SourceInput.Kind.valueOf(rs.getString("kind")), rs.getInt("char_count"),
				SourceParts.count(rs.getString("body")), cardCount,
				LocalDate.ofInstant(rs.getObject("created_at", OffsetDateTime.class).toInstant(), TimeConfig.SEOUL), job);
	}

}
