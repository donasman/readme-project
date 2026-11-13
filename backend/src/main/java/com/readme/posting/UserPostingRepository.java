package com.readme.posting;

import java.time.Instant;

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

}
