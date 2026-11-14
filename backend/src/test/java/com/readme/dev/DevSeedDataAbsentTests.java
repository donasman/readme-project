package com.readme.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.readme.IntegrationTest;

/**
 * dev 프로필이 아니면 예시 데이터(예시 사용자와 개발용 비밀번호)가 들어가지 않는다.
 */
@IntegrationTest
class DevSeedDataAbsentTests {

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcClient jdbc;

	@Test
	void exampleDataIsNotPutIn() {
		assertThat(this.context.getEnvironment().getActiveProfiles()).doesNotContain("dev");
		assertThat(this.context.getBeansOfType(DevSeedData.class)).isEmpty();
		assertThat(this.jdbc.sql("select count(*) from users where email = :email")
			.param("email", DevSeedData.MARKER_EMAIL)
			.query(Integer.class)
			.single()).isZero();
	}

}
