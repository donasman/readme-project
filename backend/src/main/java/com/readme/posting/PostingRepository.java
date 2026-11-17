package com.readme.posting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostingRepository extends JpaRepository<Posting, Long> {

	@Query("select p.id from Posting p where p.postingKey = :key")
	Optional<Long> findIdByKey(@Param("key") String key);

	/**
	 * 같은 키의 공고가 없을 때만 넣는다. 두 사람이 같은 공고를 동시에 올려도 한 행만 생긴다. 넣었으면 1, 이미 있었으면 0.
	 */
	@Modifying
	@Query(value = """
			insert into postings (posting_key, company, title, deadline_kind, deadline_date, deadline_name, apply_at,
			                      source_url, created_by, created_at, updated_at)
			values (:key, :company, :title, :deadlineKind, cast(:deadlineDate as date), cast(:deadlineName as varchar),
			        cast(:applyAt as varchar), cast(:sourceUrl as varchar), :userId, :now, :now)
			on conflict (posting_key) do nothing
			""", nativeQuery = true)
	int insertIfKeyIsNew(@Param("key") String key, @Param("company") String company, @Param("title") String title,
			@Param("deadlineKind") String deadlineKind, @Param("deadlineDate") LocalDate deadlineDate,
			@Param("deadlineName") String deadlineName, @Param("applyAt") String applyAt,
			@Param("sourceUrl") String sourceUrl, @Param("userId") long userId, @Param("now") Instant now);

	/** 공고 내용 묶음. sections 는 [{ "title": …, "items": […] }] 꼴의 JSON 글자다. */
	@Modifying
	@Query(value = "update postings set sections = cast(:sections as jsonb) where id = :postingId", nativeQuery = true)
	int setSections(@Param("postingId") long postingId, @Param("sections") String sections);

	/** 키워드를 받은 순서대로 넣는다(seq 1부터). keywords 는 겹치지 않는 대표 표기의 JSON 배열 글자다. */
	@Modifying
	@Query(value = """
			insert into posting_keywords (posting_id, keyword, seq)
			select :postingId, k.keyword, k.seq
			  from jsonb_array_elements_text(cast(:keywords as jsonb)) with ordinality as k(keyword, seq)
			""", nativeQuery = true)
	int insertKeywords(@Param("postingId") long postingId, @Param("keywords") String keywords);

	/**
	 * 공고 분석을 잡는다. 분석이 없고, 돌고 있는 분석이 없거나 staleBefore 보다 먼저 시작한 것일 때만. 잡았으면 1, 아니면 0.
	 */
	@Modifying
	@Query(value = """
			update postings set analysis_started_at = :startedAt
			 where id = :postingId and analysis is null
			   and (analysis_started_at is null or analysis_started_at < :staleBefore)
			""", nativeQuery = true)
	int startAnalysis(@Param("postingId") long postingId, @Param("startedAt") Instant startedAt,
			@Param("staleBefore") Instant staleBefore);

	/** 공고 분석을 넣는다. 이미 있으면 그대로 둔다. analysis 는 JSON 글자다. */
	@Modifying
	@Query(value = "update postings set analysis = cast(:analysis as jsonb) where id = :postingId and analysis is null",
			nativeQuery = true)
	int saveAnalysis(@Param("postingId") long postingId, @Param("analysis") String analysis);

	/** 잡은 공고 분석을 놓는다. 그 사이 다른 분석이 다시 잡았으면(시작 시각이 다르면) 그대로 둔다. */
	@Modifying
	@Query(value = """
			update postings set analysis_started_at = null
			 where id = :postingId and analysis_started_at = :startedAt
			""", nativeQuery = true)
	int endAnalysis(@Param("postingId") long postingId, @Param("startedAt") Instant startedAt);

	/**
	 * 공고 고치기. 회사명, 공고명, 마감만 바꾸고 원문 주소와 공고키는 그대로 둔다.
	 * UNKNOWN 으로는 마감이 아직 UNKNOWN 일 때만 바꾼다(마감이 있는 공고를 되돌리지 않는다). 바꿨으면 1, 아니면 0.
	 */
	@Modifying
	@Query(value = """
			update postings
			   set company = :company, title = :title, deadline_kind = :kind, deadline_date = cast(:date as date),
			       updated_at = :now
			 where id = :postingId
			   and (:kind <> 'UNKNOWN' or deadline_kind = 'UNKNOWN')
			""", nativeQuery = true)
	int edit(@Param("postingId") long postingId, @Param("company") String company, @Param("title") String title,
			@Param("kind") String kind, @Param("date") LocalDate date, @Param("now") Instant now);

	/**
	 * 날짜 넣기. 마감이 아직 UNKNOWN 일 때만 날짜(DATE)나 상시(ALWAYS)로 바꾼다.
	 * 두 사람이 같이 넣으면 먼저 넣은 사람 것만 들어간다(설계 6절 15번). 바꿨으면 1, 이미 마감이 있었으면 0.
	 *
	 * <p>newKey 가 있으면 공고키도 그 값으로 바꾼다. 다만 그 키를 이미 다른 공고가 쓰고 있으면 옛 키를 둔다.
	 * 그 사이에 같은 키의 공고가 막 올라오면 유일 제약에 걸려 500 이 난다. 두 사람이 같은 순간에 해야 생기므로 그대로 둔다.
	 */
	@Modifying
	@Query(value = """
			update postings
			   set deadline_kind = :kind, deadline_date = cast(:date as date), deadline_set_by = :userId,
			       deadline_set_at = :now, updated_at = :now,
			       posting_key = case
			           when cast(:newKey as varchar) is not null
			            and not exists (select 1 from postings other where other.posting_key = cast(:newKey as varchar))
			           then cast(:newKey as varchar)
			           else posting_key
			       end
			 where id = :postingId and deadline_kind = 'UNKNOWN'
			""", nativeQuery = true)
	int fillUnknownDeadline(@Param("postingId") long postingId, @Param("kind") String kind,
			@Param("date") LocalDate date, @Param("newKey") String newKey, @Param("userId") long userId,
			@Param("now") Instant now);

}
