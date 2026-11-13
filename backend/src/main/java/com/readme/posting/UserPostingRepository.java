package com.readme.posting;

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

}
