package com.readme.auth;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 계정을 읽는다(users, user_identities).
 */
@Component
public class AccountQueries {

	private final JdbcClient jdbc;

	AccountQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<MeResponse> findMe(long userId) {
		List<Identity> identities = this.jdbc
			.sql("select provider, provider_email from user_identities where user_id = :userId order by provider")
			.param("userId", userId)
			.query((rs, rowNum) -> new Identity(rs.getString("provider"), rs.getString("provider_email")))
			.list();
		List<String> providers = identities.stream()
			.map((identity) -> identity.provider().toLowerCase(Locale.ROOT))
			.toList();
		MeResponse.Google google = identities.stream()
			.filter((identity) -> "GOOGLE".equals(identity.provider()))
			.map((identity) -> new MeResponse.Google(identity.email()))
			.findFirst()
			.orElse(null);
		return this.jdbc.sql("select id, display_name, email, password_hash is not null as has_password from users where id = :userId")
			.param("userId", userId)
			.query((rs, rowNum) -> new MeResponse(rs.getLong("id"), rs.getString("display_name"), rs.getString("email"),
					rs.getBoolean("has_password"), providers, google))
			.optional();
	}

	/** user_identities 의 한 행. email 은 연결할 때 제공자가 준 이메일(provider_email)이다. */
	private record Identity(String provider, String email) {
	}

	/** 지금 로그인한 사람의 비밀번호를 확인할 때 쓴다. 계정이 지워졌으면 비어 있다. */
	Optional<Credentials> findCredentials(long userId) {
		return this.jdbc.sql("""
				select id, email, password_hash, email_verified_at is not null as verified
				  from users where id = :userId
				""")
			.param("userId", userId)
			.query((rs, rowNum) -> new Credentials(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"),
					rs.getBoolean("verified")))
			.optional();
	}

	/** 이메일은 대소문자를 가리지 않는다(users_email_key 가 lower(email) 로 유일하다). */
	Optional<Credentials> findCredentials(String email) {
		return this.jdbc.sql("""
				select id, email, password_hash, email_verified_at is not null as verified
				  from users where lower(email) = lower(:email)
				""")
			.param("email", email)
			.query((rs, rowNum) -> new Credentials(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"),
					rs.getBoolean("verified")))
			.optional();
	}

	/** 이 구글 계정(ID 토큰의 sub)이 연결된 계정의 id. */
	Optional<Long> findGoogleUser(String sub) {
		return this.jdbc.sql("select user_id from user_identities where provider = 'GOOGLE' and provider_user_id = :sub")
			.param("sub", sub)
			.query(Long.class)
			.optional();
	}

}
