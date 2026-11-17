package com.readme.keyword;

import static com.readme.TestRequests.loginAs;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * GET /api/keywords?q=. 순서와 note 의 규칙은 KeywordDictionaryTests 가 본다.
 */
@IntegrationTest
class KeywordControllerTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Test
	void otherFormGivesTheCanonicalNameAndTheFormThatMatched() {
		assertThat(this.mvc.get().uri("/api/keywords?q=k8s").with(loginAs(this.fixtures.user("다온")))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					[ { "label": "Kubernetes", "note": "k8s의 대표 표기" } ]
					""");
		assertThat(this.mvc.get().uri("/api/keywords?q=Redis").with(loginAs(this.fixtures.user("다온")))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					[ { "label": "Redis", "note": null } ]
					""");
	}

	@Test
	void atMostTenAndNothingForBlank() {
		long me = this.fixtures.user("다온");

		assertThat(this.mvc.get().uri("/api/keywords?q=a").with(loginAs(me))).hasStatusOk()
			.bodyJson().extractingPath("$.length()").isEqualTo(10);
		assertThat(this.mvc.get().uri("/api/keywords").param("q", "  ").with(loginAs(me))).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
		assertThat(this.mvc.get().uri("/api/keywords").with(loginAs(me))).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
	}

	@Test
	void beforeLoginIs401() {
		assertThat(this.mvc.get().uri("/api/keywords?q=k8s")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
