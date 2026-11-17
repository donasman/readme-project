package com.readme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync // 공고 분석(posting/PostingAnalyses)을 커밋 뒤 Boot 의 applicationTaskExecutor 에서 돌린다
public class ReadmeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReadmeApplication.class, args);
	}

}
