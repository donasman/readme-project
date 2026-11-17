package com.readme.inboundmail;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RegisteredSenderRepository extends JpaRepository<RegisteredSender, RegisteredSender.Key> {

	/** 없을 때만 넣는다. 넣었으면 1. */
	@Modifying
	@Query(value = """
			insert into mail_senders (user_id, email, created_at) values (:userId, :email, :now) on conflict do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("userId") long userId, @Param("email") String email, @Param("now") Instant now);

	@Modifying
	@Query(value = "delete from mail_senders where user_id = :userId and email = :email", nativeQuery = true)
	int deleteOne(@Param("userId") long userId, @Param("email") String email);

	@Query("select s.email from RegisteredSender s where s.userId = :userId")
	List<String> findEmails(@Param("userId") long userId);

	long countByUserId(long userId);

}
