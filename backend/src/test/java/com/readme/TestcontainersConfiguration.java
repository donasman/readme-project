package com.readme;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostgreSQL 을 Docker 로 띄운다. 테스트를 돌리려면 Docker 가 켜져 있어야 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/** 개발·운영의 DB 주소처럼 currentSchema=readme 를 붙인다. SQL 의 테이블 이름에 스키마를 붙이지 않아도 된다. */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16")).withUrlParam("currentSchema", "readme");
	}

}
