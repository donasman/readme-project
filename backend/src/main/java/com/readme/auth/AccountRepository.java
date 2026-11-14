package com.readme.auth;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 계정 쓰기. 같은 이메일로 두 사람이 같이 가입해도 한 행만 생기도록 조건 붙은 native 쿼리로 한다. 모두 바뀐 행 수를 돌려준다.
 */
interface AccountRepository extends JpaRepository<Account, Long> {

	/** 그 이메일(대소문자 무시)의 계정이 없을 때만 인증 전 계정을 만든다. 만들었으면 1. */
	@Modifying
	@Query(value = """
			insert into users (email, display_name, password_hash, created_at, updated_at)
			values (:email, :name, :passwordHash, :now, :now)
			on conflict ((lower(email))) do nothing
			""", nativeQuery = true)
	int insertIfEmailIsNew(@Param("email") String email, @Param("name") String name,
			@Param("passwordHash") String passwordHash, @Param("now") Instant now);

	/** 인증 전 계정에 다시 가입하면 새로 넣은 이름과 비밀번호로 바꾼다. 그 사이 인증을 마쳤으면 0. */
	@Modifying
	@Query(value = """
			update users set display_name = :name, password_hash = :passwordHash, updated_at = :now
			 where id = :id and email_verified_at is null
			""", nativeQuery = true)
	int overwriteUnverified(@Param("id") long id, @Param("name") String name,
			@Param("passwordHash") String passwordHash, @Param("now") Instant now);

	/** 인증을 마친다. 이미 마쳤으면 그때의 시각을 둔다. */
	@Modifying
	@Query(value = """
			update users set email_verified_at = coalesce(email_verified_at, :now), updated_at = :now where id = :id
			""", nativeQuery = true)
	int markVerified(@Param("id") long id, @Param("now") Instant now);

	/** 비밀번호를 바꾸고 인증도 마친 것으로 둔다. 재설정 메일을 받았다는 것이 그 이메일의 주인이라는 뜻이라서다. */
	@Modifying
	@Query(value = """
			update users set password_hash = :passwordHash, email_verified_at = coalesce(email_verified_at, :now),
			       updated_at = :now
			 where id = :id
			""", nativeQuery = true)
	int changePassword(@Param("id") long id, @Param("passwordHash") String passwordHash, @Param("now") Instant now);

	@Modifying
	@Query(value = "update users set last_login_at = :now where id = :id", nativeQuery = true)
	int recordLogin(@Param("id") long id, @Param("now") Instant now);

}
