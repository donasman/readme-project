package com.readme.common.config;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import jakarta.servlet.ServletContext;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * JSON 본문은 4MB 까지만 읽는다(application.yml 의 spring.jackson.factory.constraints.read.max-document-length).
 *
 * <p>본문은 그룹 만들기 요청을 공백으로 채워 크기를 맞춘다. 공백은 JSON 에서 뜻이 없으므로, 한도가 없으면 그룹이 만들어진다.
 */
@IntegrationTest
class JsonBodyLimitTests {

	private static final int LIMIT = 4 * 1024 * 1024;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Test
	void aBodyOfExactly4MbIsRead() {
		long me = this.fixtures.user("다온");

		assertThat(createGroup(this.mvc.post().uri("/api/groups"), me, LIMIT)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void aBodyOneByteOver4MbIs413AndCreatesNothing() {
		long me = this.fixtures.user("다온");

		assertTooLarge(createGroup(this.mvc.post().uri("/api/groups"), me, LIMIT + 1));
		assertNoGroups(me);
	}

	@Test
	void aChunkedBodyWithoutContentLengthIsRefusedToo() {
		long me = this.fixtures.user("다온");

		MvcTestResult result = createGroup(new ChunkedPost("/api/groups"), me, LIMIT + 1);

		assertThat(result.getRequest().getHeader(HttpHeaders.CONTENT_LENGTH)).isNull();
		assertThat(result.getRequest().getContentLengthLong()).isEqualTo(-1);
		assertTooLarge(result);
		assertNoGroups(me);
	}

	private MvcTestResult createGroup(AbstractMockHttpServletRequestBuilder<?> request, long userId, int size) {
		return this.mvc.perform(request.with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(paddedGroupBody(size)));
	}

	/** { "name": "한도 확인" 다음을 공백으로 채우고 } 로 닫아 꼭 size 바이트로 만든다. */
	private static byte[] paddedGroupBody(int size) {
		byte[] head = "{ \"name\": \"한도 확인\"".getBytes(StandardCharsets.UTF_8);
		byte[] body = new byte[size];
		Arrays.fill(body, (byte) ' ');
		System.arraycopy(head, 0, body, 0, head.length);
		body[size - 1] = '}';
		return body;
	}

	private static void assertTooLarge(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE)
			.bodyJson().isStrictlyEqualTo("""
				{ "code": "REQUEST_TOO_LARGE", "message": "요청이 너무 큽니다" }
				""");
	}

	private void assertNoGroups(long userId) {
		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(userId))).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
	}

	/**
	 * Tomcat 이 chunked 본문을 넘기는 꼴: Transfer-Encoding 만 있고 길이는 모른다(-1).
	 * MockMvc 는 본문이 있으면 Content-Length 를 채우므로 요청 객체를 바꿔 끼운다.
	 */
	static final class ChunkedPost extends AbstractMockHttpServletRequestBuilder<ChunkedPost> {

		ChunkedPost(String uri) {
			super(HttpMethod.POST);
			uri(uri);
			header(HttpHeaders.TRANSFER_ENCODING, "chunked");
		}

		@Override
		protected MockHttpServletRequest createServletRequest(ServletContext servletContext) {
			return new MockHttpServletRequest(servletContext) {

				@Override
				public int getContentLength() {
					return -1;
				}

				@Override
				public long getContentLengthLong() {
					return -1;
				}

			};
		}

	}

}
