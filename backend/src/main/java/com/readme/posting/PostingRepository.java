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

	/**
	 * 날짜 넣기. 마감이 아직 UNKNOWN 일 때만 바꾼다. 두 사람이 같이 넣으면 먼저 넣은 사람 것만 들어간다(설계 6절 15번).
	 * 바꿨으면 1, 이미 날짜가 있었으면 0.
	 */
	@Modifying
	@Query(value = """
			update postings
			   set deadline_kind = 'DATE', deadline_date = :date, deadline_set_by = :userId, deadline_set_at = :now,
			       updated_at = :now
			 where id = :postingId and deadline_kind = 'UNKNOWN'
			""", nativeQuery = true)
	int fillUnknownDeadline(@Param("postingId") long postingId, @Param("date") LocalDate date,
			@Param("userId") long userId, @Param("now") Instant now);

}
