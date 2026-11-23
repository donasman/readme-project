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

	/** 카드 우선순위 한 줄. 지운 뒤에만 부른다. 그 사이 지워진 카드면 넣지 않고 0 이다(LLM 을 기다리는 동안 지울 수 있다). */
	@Modifying
	@Query(value = """
			insert into user_posting_cards (user_id, posting_id, card_id, position, reason)
			select :userId, :postingId, id, :position, cast(:reason as varchar) from experience_cards where id = :cardId
			""", nativeQuery = true)
	int insertCard(@Param("userId") long userId, @Param("postingId") long postingId, @Param("cardId") long cardId,
			@Param("position") int position, @Param("reason") String reason);

	/** 이 내 공고는 내 문항 목록을 쓴다(문항 매칭 QD2). 이 뒤로는 공고 분석의 문항을 따라가지 않는다. */
	@Modifying
	@Query(value = "update user_postings set questions_saved = true where user_id = :userId and posting_id = :postingId",
			nativeQuery = true)
	int markQuestionsSaved(@Param("userId") long userId, @Param("postingId") long postingId);

	/** 문항 하나를 넣고 새 id 를 돌려준다. */
	@Query(value = """
			insert into user_posting_questions (user_id, posting_id, position, text, char_limit)
			values (:userId, :postingId, :position, :text, cast(:limit as integer)) returning id
			""", nativeQuery = true)
	long insertQuestion(@Param("userId") long userId, @Param("postingId") long postingId,
			@Param("position") int position, @Param("text") String text, @Param("limit") Integer limit);

	/** 문항의 자리와 글, 글자 수를 고친다. 유형과 고른 카드는 그대로다. */
	@Modifying
	@Query(value = """
			update user_posting_questions set position = :position, text = :text, char_limit = cast(:limit as integer)
			 where id = :id
			""", nativeQuery = true)
	int updateQuestion(@Param("id") long id, @Param("position") int position, @Param("text") String text,
			@Param("limit") Integer limit);

	/** 문항을 지운다. 고른 카드는 외래 키로 함께 지워진다. */
	@Modifying
	@Query(value = "delete from user_posting_questions where id = :id", nativeQuery = true)
	int deleteQuestion(@Param("id") long id);

	/** LLM 이 고른 문항 유형. 버렸으면 null. */
	@Modifying
	@Query(value = "update user_posting_questions set type = cast(:type as varchar) where id = :id", nativeQuery = true)
	int updateQuestionType(@Param("id") long id, @Param("type") String type);

	/** 문항의 카드를 모두 지운다. */
	@Modifying
	@Query(value = "delete from user_posting_question_cards where question_id = :questionId", nativeQuery = true)
	int deleteQuestionCards(@Param("questionId") long questionId);

	/**
	 * 문항의 카드 한 장. 지운 뒤에만 부른다. emphasis 는 살릴 칸을 쉼표로 이은 것이다. 그 사이 지워진 카드면 넣지 않고 0 이다(LLM 을
	 * 기다리는 동안 지울 수 있다).
	 */
	@Modifying
	@Query(value = """
			insert into user_posting_question_cards (question_id, card_id, position, reason, emphasis)
			select :questionId, id, :position, cast(:reason as varchar), cast(:emphasis as varchar)
			  from experience_cards where id = :cardId
			""", nativeQuery = true)
	int insertQuestionCard(@Param("questionId") long questionId, @Param("cardId") long cardId,
			@Param("position") int position, @Param("reason") String reason, @Param("emphasis") String emphasis);

}
