package com.readme;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReadmeApplicationTests {

	/**
	 * 서버가 뜨는지 본다. Flyway 가 V1, V2 를 실제 PostgreSQL 에 적용하는 것까지 포함한다.
	 */
	@Test
	void contextLoads() {
	}

}
