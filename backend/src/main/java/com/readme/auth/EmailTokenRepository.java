package com.readme.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

	/** 아직 쓰지 않았고 지나지 않은 토큰. */
	@Query("""
			select t from EmailToken t
			 where t.tokenHash = :tokenHash and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now
			""")
	Optional<EmailToken> findUsable(@Param("tokenHash") String tokenHash, @Param("purpose") EmailToken.Purpose purpose,
			@Param("now") Instant now);

	/** 토큰을 쓴다. 같은 링크를 두 번 눌러도 한 번만 된다. 썼으면 1, 이미 쓴 토큰이면 0. */
	@Modifying
	@Query(value = "update email_tokens set used_at = :now where id = :id and used_at is null", nativeQuery = true)
	int use(@Param("id") long id, @Param("now") Instant now);

	/** 새 링크를 보내기 전에, 그 사람에게 보낸 같은 종류의 쓰지 않은 링크를 지운다. 메일함의 옛 링크는 쓸 수 없게 된다. */
	@Modifying
	@Query(value = "delete from email_tokens where user_id = :userId and purpose = :purpose and used_at is null",
			nativeQuery = true)
	int deleteUnused(@Param("userId") long userId, @Param("purpose") String purpose);

}
