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

	/**
	 * 그 이메일(대소문자 무시)의 계정이 없을 때만 인증 전 계정을 만든다. 만들었으면 1. 동의한 것은 지금 시각을 넣는다.
	 * 동의는 null 이 될 수 있는 시각 대신 참·거짓으로 받는다. native 쿼리에 null 시각을 넘기면 타입을 알 수 없어서다.
	 * case 안의 시각은 cast 한다. 하지 않으면 PostgreSQL 이 글자로 본다.
	 */
	@Modifying
	@Query(value = """
			insert into users (email, display_name, password_hash, birth_gender_consent_at, event_notice_consent_at,
			                   created_at, updated_at)
			values (:email, :name, :passwordHash, case when :birthGenderConsent then cast(:now as timestamptz) end,
			        case when :eventNoticeConsent then cast(:now as timestamptz) end, :now, :now)
			on conflict ((lower(email))) do nothing
			""", nativeQuery = true)
	int insertIfEmailIsNew(@Param("email") String email, @Param("name") String name,
			@Param("passwordHash") String passwordHash, @Param("birthGenderConsent") boolean birthGenderConsent,
			@Param("eventNoticeConsent") boolean eventNoticeConsent, @Param("now") Instant now);

	/**
	 * 인증 전 계정에 다시 가입하면 새로 넣은 이름과 비밀번호, 동의로 바꾼다. 앞서 가입한 사람의 동의는 남기지 않는다.
	 * 그 사이 인증을 마쳤으면 0.
	 */
	@Modifying
	@Query(value = """
			update users set display_name = :name, password_hash = :passwordHash,
			       birth_gender_consent_at = case when :birthGenderConsent then cast(:now as timestamptz) end,
			       event_notice_consent_at = case when :eventNoticeConsent then cast(:now as timestamptz) end,
			       updated_at = :now
			 where id = :id and email_verified_at is null
			""", nativeQuery = true)
	int overwriteUnverified(@Param("id") long id, @Param("name") String name,
			@Param("passwordHash") String passwordHash, @Param("birthGenderConsent") boolean birthGenderConsent,
			@Param("eventNoticeConsent") boolean eventNoticeConsent, @Param("now") Instant now);

	/** 생년월일·성별의 수집·이용 동의를 남긴다(구글 동의 화면에서 허락했을 때). 이미 있으면 처음 동의한 때를 둔다. */
	@Modifying
	@Query(value = """
			update users set birth_gender_consent_at = coalesce(birth_gender_consent_at, :now) where id = :id
			""", nativeQuery = true)
	int recordBirthGenderConsent(@Param("id") long id, @Param("now") Instant now);

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

	/** 표시 이름을 바꾼다. 계정이 없으면(지워졌으면) 0. */
	@Modifying
	@Query(value = "update users set display_name = :name, updated_at = :now where id = :id", nativeQuery = true)
	int rename(@Param("id") long id, @Param("name") String name, @Param("now") Instant now);

	@Modifying
	@Query(value = "update users set last_login_at = :now where id = :id", nativeQuery = true)
	int recordLogin(@Param("id") long id, @Param("now") Instant now);

	/** 구글로 처음 온 사람: 그 이메일의 계정이 없을 때만 인증을 마친, 비밀번호 없는 계정을 만든다. 만들었으면 1. */
	@Modifying
	@Query(value = """
			insert into users (email, display_name, email_verified_at, created_at, updated_at)
			values (:email, :name, :now, :now, :now)
			on conflict ((lower(email))) do nothing
			""", nativeQuery = true)
	int insertGoogleUserIfEmailIsNew(@Param("email") String email, @Param("name") String name,
			@Param("now") Instant now);

	/**
	 * 구글이 이메일의 주인임을 확인한 인증 전 계정: 비밀번호를 지우고, 이름을 구글의 것으로 바꾸고, 인증을 마친다(로그인 설계 3.2).
	 * 앞서 가입한 사람의 동의도 지운다. 그 사이 인증을 마쳤으면 0.
	 */
	@Modifying
	@Query(value = """
			update users set display_name = :name, password_hash = null, email_verified_at = :now,
			       birth_gender_consent_at = null, event_notice_consent_at = null, updated_at = :now
			 where id = :id and email_verified_at is null
			""", nativeQuery = true)
	int takeOverUnverified(@Param("id") long id, @Param("name") String name, @Param("now") Instant now);

	/**
	 * 구글 계정(ID 토큰의 sub)을 연결한다. 연결했으면 1. 그 구글 계정이 이미 연결되어 있거나(같은 사람이 동시에 들어왔다)
	 * 이 계정에 다른 구글 계정이 연결되어 있으면 0.
	 */
	@Modifying
	@Query(value = """
			insert into user_identities (user_id, provider, provider_user_id, provider_email, created_at)
			values (:userId, 'GOOGLE', :sub, :email, :now)
			on conflict do nothing
			""", nativeQuery = true)
	int linkGoogle(@Param("userId") long userId, @Param("sub") String sub, @Param("email") String email,
			@Param("now") Instant now);

}
