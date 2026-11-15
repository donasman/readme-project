package com.readme.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProfileRepository extends JpaRepository<Profile, Long> {

	/** 프로필 행이 없으면 빈 행을 만든다. 같은 사람이 처음 저장을 두 번 같이 보내도 한 행만 생긴다. */
	@Modifying
	@Query(value = "insert into profiles (user_id) values (:userId) on conflict do nothing", nativeQuery = true)
	void insertIfAbsent(@Param("userId") long userId);

}
