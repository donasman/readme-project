package com.readme.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * 20MB 를 넘는 파일을 실제 서버(Tomcat)에 보낸다. MockMvc 는 multipart 크기 제한(max-file-size)을 거치지 않아서다.
 * Tomcat 이 낸 크기 오류도 SOURCE_TOO_LARGE 로 답하는지 본다(CardSourceController 의 tooLarge).
 *
 * <p>실제 서버가 필요한데 서버를 하나 더 띄우면 메모리가 든다. 그래서 dev/DevProfileTests 와 설정을 똑같이 두어
 * 그 테스트가 띄운 내장 서버를 함께 쓴다. 저쪽 설정이 바뀌면 여기도 같이 바꾼다.
 */
@IntegrationTest
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "test", "dev" })
class CardSourceUploadLimitTests {

	private static final String XSRF = "upload-limit-xsrf";

	@Autowired
	Fixtures fixtures;

	@Value("${local.server.port}")
	int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void tomcatsLimitAlsoAnswersSourceTooLarge() throws Exception {
		long me = this.fixtures.passwordUser("다온", "upload-limit-1234", true);
		String session = login(this.fixtures.emailOf(me), "upload-limit-1234");

		HttpResponse<String> response = upload(session, new byte[(int) SourceInput.MAX_BYTES + 1]);

		assertThat(response.statusCode()).isEqualTo(413);
		assertThat(response.body()).contains("\"code\":\"SOURCE_TOO_LARGE\"");
	}

	@Test
	void aFileUnderTheLimitGoesThrough() throws Exception {
		long me = this.fixtures.passwordUser("다온", "upload-limit-1234", true);
		String session = login(this.fixtures.emailOf(me), "upload-limit-1234");

		HttpResponse<String> response = upload(session, "실제 서버로 올린 글".getBytes(StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.body()).contains("\"kind\":\"file\"");
	}

	/** 실제 로그인. 응답의 SESSION 쿠키 값을 돌려준다. */
	private String login(String email, String password) throws IOException, InterruptedException {
		HttpResponse<String> response = this.http.send(HttpRequest.newBuilder(uri("/api/auth/login"))
			.header("Content-Type", "application/json")
			.header("Cookie", "XSRF-TOKEN=" + XSRF)
			.header("X-XSRF-TOKEN", XSRF)
			.POST(HttpRequest.BodyPublishers.ofString("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password)))
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		return response.headers().allValues("Set-Cookie").stream()
			.filter((cookie) -> cookie.startsWith("SESSION="))
			.map((cookie) -> cookie.substring("SESSION=".length(), cookie.indexOf(';')))
			.reduce((first, last) -> last)
			.orElseThrow();
	}

	private HttpResponse<String> upload(String session, byte[] file) throws IOException, InterruptedException {
		String boundary = "readme-test-boundary";
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n"
				+ "Content-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(file);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return this.http.send(HttpRequest.newBuilder(uri("/api/card-sources"))
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.header("Cookie", "SESSION=" + session + "; XSRF-TOKEN=" + XSRF)
			.header("X-XSRF-TOKEN", XSRF)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

}
