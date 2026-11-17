package com.readme.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러에서 나온 오류가 { code, message } 로 나가는지 본다. 서버 전체를 띄우지 않으므로 Docker 가 필요 없다.
 */
class GlobalExceptionHandlerTests {

	private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ProbeController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build());

	@Test
	void apiExceptionUsesItsCodeStatusAndMessage() {
		assertThat(this.mvc.get().uri("/probe/conflict"))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson().isStrictlyEqualTo("""
				{ "code": "PROBE_CONFLICT", "message": "이미 있습니다" }
				""");
	}

	@Test
	void apiExceptionCanReplaceTheMessage() {
		assertThat(this.mvc.get().uri("/probe/conflict-with-message"))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson().isStrictlyEqualTo("""
				{ "code": "PROBE_CONFLICT", "message": "그 공고는 이미 이 그룹에 있습니다" }
				""");
	}

	@Test
	void valuesAttachedToTheErrorSitBesideCodeAndMessage() {
		assertThat(this.mvc.get().uri("/probe/conflict-with-details"))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson().isStrictlyEqualTo("""
				{ "code": "PROBE_CONFLICT", "message": "이미 있습니다", "postId": 12 }
				""");
	}

	@Test
	void invalidBodyNamesTheField() {
		assertThat(this.mvc.post().uri("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("""
				{ "company": " " }
				"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().isStrictlyEqualTo("""
				{ "code": "INVALID_INPUT", "message": "입력값을 확인해 주세요: company" }
				""");
	}

	@Test
	void unreadableBodyIsInvalidInput() {
		assertThat(this.mvc.post().uri("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	/** 본문 4MB 한도는 application.yml 에 있어 common/config/JsonBodyLimitTests 가 본다. 여기서는 Jackson 의 기본 한도(숫자 1000자리)로 본다. */
	@Test
	void bodyPastJacksonsReadLimitIs413() {
		assertThat(this.mvc.post().uri("/probe/validated").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"company\": %s }".formatted("1".repeat(1001))))
			.hasStatus(HttpStatus.CONTENT_TOO_LARGE)
			.bodyJson().isStrictlyEqualTo("""
				{ "code": "REQUEST_TOO_LARGE", "message": "요청이 너무 큽니다" }
				""");
	}

	@Test
	void wrongPathValueTypeIsInvalidInput() {
		assertThat(this.mvc.get().uri("/probe/groups/abc"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	@Test
	void unsupportedMethodIs405() {
		assertThat(this.mvc.delete().uri("/probe/conflict"))
			.hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
			.bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
	}

	@Test
	void unexpectedErrorIs500WithoutDetails() {
		assertThat(this.mvc.get().uri("/probe/broken"))
			.hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.bodyJson().isStrictlyEqualTo("""
				{ "code": "INTERNAL_ERROR", "message": "서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요" }
				""");
	}

	/** 기능 패키지가 자기 오류 코드를 두는 방식 그대로. */
	enum ProbeErrorCode implements ErrorCode {

		PROBE_CONFLICT;

		@Override
		public HttpStatus status() {
			return HttpStatus.CONFLICT;
		}

		@Override
		public String message() {
			return "이미 있습니다";
		}

	}

	record ProbeBody(@NotBlank String company) {
	}

	@RestController
	static class ProbeController {

		@GetMapping("/probe/conflict")
		void conflict() {
			throw new ApiException(ProbeErrorCode.PROBE_CONFLICT);
		}

		@GetMapping("/probe/conflict-with-message")
		void conflictWithMessage() {
			throw new ApiException(ProbeErrorCode.PROBE_CONFLICT, "그 공고는 이미 이 그룹에 있습니다");
		}

		@GetMapping("/probe/conflict-with-details")
		void conflictWithDetails() {
			throw new ApiException(ProbeErrorCode.PROBE_CONFLICT).with("postId", 12L);
		}

		@PostMapping("/probe/validated")
		void validated(@Valid @RequestBody ProbeBody body) {
		}

		@GetMapping("/probe/groups/{groupId}")
		void group(@PathVariable long groupId) {
		}

		@GetMapping("/probe/broken")
		void broken() {
			throw new IllegalStateException("내부 정보가 응답에 실리면 안 된다");
		}

	}

}
