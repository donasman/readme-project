package com.readme.posting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserPostingRepository extends JpaRepository<UserPosting, UserPosting.Key> {

	/** 없을 때만 넣는다(미정). 두 요청이 동시에 와도 하나만 들어간다. 넣었으면 1, 이미 있었으면 0. */
	@Modifying
	@Query(value = "insert into user_postings (user_id, posting_id) values (:userId, :postingId) on conflict do nothing",
			nativeQuery = true)
	int insertIfAbsent(@Param("userId") long userId, @Param("postingId") long postingId);

	/** 지원 상태를 이 값으로 둔다. 행이 없으면 만든다. 같은 요청을 두 번 보내도 결과가 같다. */
	@Modifying
	@Query(value = """
			insert into user_postings (user_id, posting_id, decision, stage, result, created_at, updated_at)
			values (:userId, :postingId, :decision, cast(:stage as varchar), cast(:result as varchar), :now, :now)
			on conflict (user_id, posting_id)
			do update set decision = excluded.decision, stage = excluded.stage, result = excluded.result,
			              updated_at = excluded.updated_at
			""", nativeQuery = true)
	int upsertStatus(@Param("userId") long userId, @Param("postingId") long postingId,
			@Param("decision") String decision, @Param("stage") String stage, @Param("result") String result,
			@Param("now") Instant now);

	/** 메모만 이 값으로 둔다(지원 상태는 그대로). 내 공고 행이 있으면 1, 없으면 0. */
	@Modifying
	@Query(value = """
			update user_postings set memo = cast(:memo as varchar), updated_at = :now
			 where user_id = :userId and posting_id = :postingId
			""", nativeQuery = true)
	int updateMemo(@Param("userId") long userId, @Param("postingId") long postingId, @Param("memo") String memo,
			@Param("now") Instant now);

	/** 내 공고 행을 지운다. 전형 일정은 외래 키로 함께 지워진다. */
	@Modifying
	@Query(value = "delete from user_postings where user_id = :userId and posting_id = :postingId", nativeQuery = true)
	int deleteRow(@Param("userId") long userId, @Param("postingId") long postingId);

	/** 이 단계의 전형 일정을 이 값으로 둔다. 같은 요청을 두 번 보내도 결과가 같다. */
	@Modifying
	@Query(value = """
			insert into user_posting_schedules (user_id, posting_id, stage, on_date, at_time, updated_at)
			values (:userId, :postingId, :stage, :date, cast(:time as time), :now)
			on conflict (user_id, posting_id, stage)
			do update set on_date = excluded.on_date, at_time = excluded.at_time, updated_at = excluded.updated_at
			""", nativeQuery = true)
	int upsertSchedule(@Param("userId") long userId, @Param("postingId") long postingId, @Param("stage") String stage,
			@Param("date") LocalDate date, @Param("time") LocalTime time, @Param("now") Instant now);

	/** 이 내 공고의 카드 우선순위를 모두 지운다(카드 우선순위 설계). */
	@Modifying
	@Query(value = "delete from user_posting_cards where user_id = :userId and posting_id = :postingId", nativeQuery = true)
	int deleteCards(@Param("userId") long userId, @Param("postingId") long postingId);

	/** 카드 우선순위 한 줄. 지운 뒤에만 부른다. */
	@Modifying
	@Query(value = """
			insert into user_posting_cards (user_id, posting_id, card_id, position, reason)
			values (:userId, :postingId, :cardId, :position, cast(:reason as varchar))
			""", nativeQuery = true)
	int insertCard(@Param("userId") long userId, @Param("postingId") long postingId, @Param("cardId") long cardId,
			@Param("position") int position, @Param("reason") String reason);

}
