package com.readme.grouppost;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 올리기와 다시 올리기는 두 사람이 같이 눌러도 한 번만 되어야 한다. 그래서 조건이 붙은 insert·update 로 쓴다.
 */
interface GroupPostRepository extends JpaRepository<GroupPost, GroupPost.Key> {

	/** 이 그룹에 처음 올리는 공고면 넣는다. 넣었으면 1, 이미 있었으면(내렸던 공고 포함) 0. */
	@Modifying
	@Query(value = """
			insert into group_posts (group_id, posting_id, posted_by, posted_at)
			values (:groupId, :postingId, :userId, :now)
			on conflict do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("groupId") long groupId, @Param("postingId") long postingId, @Param("userId") long userId,
			@Param("now") Instant now);

	/**
	 * 내렸던 공고를 다시 올린다. 같은 행이 되살아나 댓글과 관심이 그대로 남는다(설계 가정 4).
	 * 올린 사람과 올린 시각은 다시 올린 사람과 지금으로 바꾼다. 되살렸으면 1, 내린 공고가 아니었으면 0.
	 */
	@Modifying
	@Query(value = """
			update group_posts
			   set taken_down_at = null, taken_down_by = null, posted_by = :userId, posted_at = :now
			 where group_id = :groupId and posting_id = :postingId and taken_down_at is not null
			""", nativeQuery = true)
	int reviveIfTakenDown(@Param("groupId") long groupId, @Param("postingId") long postingId,
			@Param("userId") long userId, @Param("now") Instant now);

}
