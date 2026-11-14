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
		List<String> providers = this.jdbc.sql("select provider from user_identities where user_id = :userId order by provider")
			.param("userId", userId)
			.query((rs, rowNum) -> rs.getString("provider").toLowerCase(Locale.ROOT))
			.list();
		return this.jdbc.sql("select id, display_name, email, password_hash is not null as has_password from users where id = :userId")
			.param("userId", userId)
			.query((rs, rowNum) -> new MeResponse(rs.getLong("id"), rs.getString("display_name"), rs.getString("email"),
					rs.getBoolean("has_password"), providers))
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

}
