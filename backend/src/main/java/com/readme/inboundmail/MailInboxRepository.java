package com.readme.inboundmail;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MailInboxRepository extends JpaRepository<MailInbox, Long> {

	/** 없을 때만 만든다. 설정을 두 번 같이 열어도 주소가 하나다. 만들었으면 1. */
	@Modifying
	@Query(value = """
			insert into mail_inboxes (user_id, token, created_at, updated_at) values (:userId, :token, :now, :now)
			on conflict (user_id) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("userId") long userId, @Param("token") String token, @Param("now") Instant now);

	/** 수신 주소를 새 토큰으로 바꾼다. 앞 토큰으로 온 메일은 더 받지 않는다. */
	@Modifying
	@Query(value = "update mail_inboxes set token = :token, updated_at = :now where user_id = :userId", nativeQuery = true)
	int renew(@Param("userId") long userId, @Param("token") String token, @Param("now") Instant now);

	/** 토큰(소문자)의 주인. */
	@Query("select i.userId from MailInbox i where i.token = :token")
	Optional<Long> findUserIdByToken(@Param("token") String token);

}
