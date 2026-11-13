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
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 개발용 사용자와 예시 그룹·공고를 넣는다(resources/dev/seed.sql). 서버가 뜰 때 한 번 보고, 이미 있으면 넣지 않는다.
 *
 * <p>Flyway 마이그레이션(V…)이 아니므로 운영 DB 에는 들어가지 않는다. 예시를 바꾸면 로컬 DB 를
 * {@code docker compose down -v} 로 지우고 다시 띄워야 반영된다.
 */
@Profile("dev & !prod")
@Component
class DevSeedData implements ApplicationRunner {

	/** 이 이메일의 사용자가 있으면 이미 넣은 것으로 본다. */
	static final String MARKER_EMAIL = "daon@example.com";

	private static final Logger log = LoggerFactory.getLogger(DevSeedData.class);

	private final JdbcClient jdbc;

	private final DataSource dataSource;

	private final TransactionTemplate transaction;

	DevSeedData(JdbcClient jdbc, DataSource dataSource, TransactionTemplate transaction) {
		this.jdbc = jdbc;
		this.dataSource = dataSource;
		this.transaction = transaction;
	}

	@Override
	public void run(ApplicationArguments args) {
		boolean seeded = this.jdbc.sql("select exists (select 1 from users where email = :email)")
			.param("email", MARKER_EMAIL)
			.query(Boolean.class)
			.single();
		if (seeded) {
			return;
		}
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("dev/seed.sql"));
		populator.setSqlScriptEncoding("UTF-8");
		this.transaction.executeWithoutResult((status) -> populator.execute(this.dataSource));
		log.info("개발용 예시 데이터를 넣었다. 임시 로그인: POST /api/auth/dev-login {\"email\": \"{}\"}", MARKER_EMAIL);
	}

}
