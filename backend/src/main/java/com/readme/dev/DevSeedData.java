package com.readme.dev;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 개발용 사용자와 예시 그룹·공고를 넣는다(resources/dev/seed.sql). 서버가 뜰 때 한 번 보고, 이미 있으면 넣지 않는다.
 *
 * <p>예시 사용자(이메일이 @example.com)는 모두 {@link #DEV_PASSWORD} 로 로그인한다(POST /api/auth/login).
 * 비밀번호가 없는 예시 사용자에게는 뜰 때마다 이 비밀번호를 넣는다. 비밀번호가 생기기 전에 넣은 로컬 DB 도 그대로 쓸 수 있다.
 *
 * <p>Flyway 마이그레이션(V…)이 아니므로 운영 DB 에는 들어가지 않는다. 예시를 바꾸면 로컬 DB 를
 * {@code docker compose down -v} 로 지우고 다시 띄워야 반영된다.
 */
@Profile("dev & !prod")
@Component
class DevSeedData implements ApplicationRunner {

	/** 이 이메일의 사용자가 있으면 이미 넣은 것으로 본다. */
	static final String MARKER_EMAIL = "daon@example.com";

	/** 예시 사용자 모두의 비밀번호. 내 PC 의 DB 에만 들어간다. */
	static final String DEV_PASSWORD = "readme1234";

	private static final Logger log = LoggerFactory.getLogger(DevSeedData.class);

	private final JdbcClient jdbc;

	private final DataSource dataSource;

	private final TransactionTemplate transaction;

	private final PasswordEncoder passwordEncoder;

	DevSeedData(JdbcClient jdbc, DataSource dataSource, TransactionTemplate transaction,
			PasswordEncoder passwordEncoder) {
		this.jdbc = jdbc;
		this.dataSource = dataSource;
		this.transaction = transaction;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		boolean seeded = this.jdbc.sql("select exists (select 1 from users where email = :email)")
			.param("email", MARKER_EMAIL)
			.query(Boolean.class)
			.single();
		if (!seeded) {
			ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("dev/seed.sql"));
			populator.setSqlScriptEncoding("UTF-8");
			this.transaction.executeWithoutResult((status) -> populator.execute(this.dataSource));
			log.info("개발용 예시 데이터를 넣었다");
		}
		int filled = this.jdbc.sql("""
				update users set password_hash = :hash, email_verified_at = coalesce(email_verified_at, now())
				 where email like '%@example.com' and password_hash is null
				""")
			.param("hash", this.passwordEncoder.encode(DEV_PASSWORD))
			.update();
		if (filled > 0) {
			log.info("예시 사용자 {}명에게 개발용 비밀번호를 넣었다", filled);
		}
		log.info("예시 사용자로 로그인: POST /api/auth/login {\"email\": \"{}\", \"password\": \"{}\"}", MARKER_EMAIL,
				DEV_PASSWORD);
	}

}
