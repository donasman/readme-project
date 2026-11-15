package com.readme.posting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/** 내 공고 상세의 맞는 카드(공고 설계 4.8). 공고 분석은 DB 에 바로 넣는다. */
@IntegrationTest
class MyPostingMatchTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Test
	void detailShowsMyMatchingCardsAndUnmetRequirements() {
		long me = this.fixtures.user("다온");
		long other = this.fixtures.user("가람");
		long posting = this.fixtures.posting("예시랩", "백엔드 개발자", "UNKNOWN", null);
		this.fixtures.status(me, posting, "UNDECIDED", null, null);
		this.jdbc.sql("update postings set analysis = cast(:analysis as jsonb) where id = :id")
			.param("id", posting)
			.param("analysis", """
					{ "must": ["Java 기반 API 개발", "Kubernetes 운영"], "nice": ["Redis 캐시 경험", "성실함"],
					  "duties": [], "keywords": ["Java", "Docker"], "questions": [] }
					""")
			.update();
		card(me, "알림 배치", "Docker");
		long api = card(me, "주문 API 개선", "스프링부트", "Redis");
		card(other, "남의 카드", "Java", "Kubernetes");

		assertThat(this.mvc.get().uri("/api/me/postings/{id}", posting).with(loginAs(me))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "posting": {
					    "matches": [ { "cardId": %d, "title": "주문 API 개선", "overlap": ["Java", "Redis"] },
					                 { "title": "알림 배치", "overlap": ["Docker"] } ],
					    "unmatched": ["Kubernetes 운영"] } }
					""".formatted(api));
	}

	private long card(long userId, String title, String... tags) {
		String json = "{ \"title\": \"%s\", \"tags\": [%s] }".formatted(title,
				String.join(", ", java.util.Arrays.stream(tags).map((t) -> "\"" + t + "\"").toList()));
		var result = this.mvc.post()
			.uri("/api/cards")
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return this.jdbc.sql("select max(id) from experience_cards where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

}
