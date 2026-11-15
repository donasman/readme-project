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
