package com.readme;

import org.springframework.boot.SpringApplication;

/**
 * compose.yaml 없이 서버를 띄워 보고 싶을 때 쓴다. DB 를 Testcontainers 로 띄운다.
 * 실행: gradlew bootTestRun
 */
public class TestReadmeApplication {

	public static void main(String[] args) {
		SpringApplication.from(ReadmeApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
