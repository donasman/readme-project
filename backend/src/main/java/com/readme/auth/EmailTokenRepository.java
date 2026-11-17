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

	/**
	 * 그 사람에게 보낸 같은 종류의 쓰지 않은 링크를 쓴 것으로 두어 쓸 수 없게 한다. 메일함의 옛 링크는 410 이 된다.
	 * 지우지 않는다. 보낸 링크의 수를 created_at 으로 세기 때문이다. 24시간이 지나면 deleteSpent 가 지운다.
	 */
	@Modifying
	@Query(value = """
			update email_tokens set used_at = :now
			 where user_id = :userId and purpose = :purpose and used_at is null
			""", nativeQuery = true)
	int cancelUnused(@Param("userId") long userId, @Param("purpose") String purpose, @Param("now") Instant now);

	/**
	 * 더 쓸 데가 없는 링크를 지운다. before 와 같거나 앞서 만들었고(countCreatedSince 가 세지 않는다), 썼거나 지난 것이다.
	 * 지운 행 수를 돌려준다.
	 */
	@Modifying
	@Query(value = """
			delete from email_tokens
			 where created_at <= :before and (used_at is not null or expires_at <= :now)
			""", nativeQuery = true)
	int deleteSpent(@Param("before") Instant before, @Param("now") Instant now);

	/** 그 사람에게 since 뒤에 만든 같은 종류의 링크 수. 쓴 것과 쓸 수 없게 한 것도 센다. */
	@Query("""
			select count(t) from EmailToken t
			 where t.userId = :userId and t.purpose = :purpose and t.createdAt > :since
			""")
	long countCreatedSince(@Param("userId") long userId, @Param("purpose") EmailToken.Purpose purpose,
			@Param("since") Instant since);

}
