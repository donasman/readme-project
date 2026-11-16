package com.readme.grouppost;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관심 켜기·끄기는 같은 요청이 두 번 와도 결과가 같아야 한다(PUT). 그래서 있으면 두는 insert 와 없어도 되는 delete 로 쓴다.
 * 둘 다 바로 실행되므로, 같은 트랜잭션에서 뒤에 읽는 조회가 바뀐 값을 본다.
 */
interface GroupPostInterestRepository extends JpaRepository<GroupPostInterest, GroupPostInterest.Key> {

	@Modifying
	@Query(value = """
			insert into group_post_interests (group_id, posting_id, user_id)
			values (:groupId, :postingId, :userId)
			on conflict do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("groupId") long groupId, @Param("postingId") long postingId, @Param("userId") long userId);

	@Modifying
	@Query(value = """
			delete from group_post_interests
			 where group_id = :groupId and posting_id = :postingId and user_id = :userId
			""", nativeQuery = true)
	int deleteIfPresent(@Param("groupId") long groupId, @Param("postingId") long postingId, @Param("userId") long userId);

	/** 이 사람이 모든 그룹에서 켜 둔 이 공고의 관심을 끈다. */
	@Modifying
	@Query(value = "delete from group_post_interests where posting_id = :postingId and user_id = :userId",
			nativeQuery = true)
	int deleteEverywhere(@Param("postingId") long postingId, @Param("userId") long userId);

}
