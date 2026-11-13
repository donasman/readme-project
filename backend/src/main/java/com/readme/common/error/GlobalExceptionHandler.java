package com.readme.common.error;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 컨트롤러에서 나온 오류를 { code, message } 로 바꾼다.
 *
 * <p>컨트롤러에 닿기 전에 보안 필터가 막은 요청(401, CSRF 403)은 SecurityConfig 가 같은 꼴로 쓴다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException exception) {
		ErrorCode code = exception.errorCode();
		return ResponseEntity.status(code.status()).body(new ApiError(code.name(), exception.getMessage()));
	}

	/** @Valid 검사에 걸린 본문. 어느 칸이 틀렸는지 메시지에 붙인다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException exception) {
		String fields = exception.getBindingResult().getFieldErrors().stream()
			.map(FieldError::getField)
			.distinct()
			.collect(Collectors.joining(", "));
		CommonErrorCode code = CommonErrorCode.INVALID_INPUT;
		String message = fields.isEmpty() ? code.message() : code.message() + ": " + fields;
		return ResponseEntity.status(code.status()).body(new ApiError(code.name(), message));
	}

	/** JSON 이 아닌 본문, 주소나 쿼리의 값 꼴이 틀린 경우(/groups/abc). 이 둘은 아래의 ErrorResponse 가 아니라 따로 받는다. */
	@ExceptionHandler({ HttpMessageNotReadableException.class, TypeMismatchException.class })
	ResponseEntity<ApiError> handleUnreadable(Exception exception) {
		CommonErrorCode code = CommonErrorCode.INVALID_INPUT;
		return ResponseEntity.status(code.status()).body(ApiError.of(code));
	}

	/**
	 * 나머지 전부. 스프링 MVC 가 내는 오류(본문을 읽을 수 없음, 없는 주소, 지원하지 않는 메서드 등)는 그 상태 코드를 지키고,
	 * 예상하지 못한 오류는 기록을 남기고 500 을 준다.
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleOther(Exception exception) throws Exception {
		if (exception instanceof AuthenticationException || exception instanceof AccessDeniedException) {
			// 보안 오류는 Spring Security 가 401·403 으로 바꾸도록 그대로 넘긴다.
			throw exception;
		}
		if (exception instanceof ErrorResponse errorResponse) {
			HttpStatusCode status = errorResponse.getStatusCode();
			CommonErrorCode code = codeFor(status);
			return ResponseEntity.status(status).body(new ApiError(code.name(), code.message()));
		}
		log.error("처리하지 못한 오류", exception);
		return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.status()).body(ApiError.of(CommonErrorCode.INTERNAL_ERROR));
	}

	private static CommonErrorCode codeFor(HttpStatusCode status) {
		if (status.is5xxServerError()) {
			return CommonErrorCode.INTERNAL_ERROR;
		}
		return switch (status.value()) {
			case 404 -> CommonErrorCode.NOT_FOUND;
			case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
			default -> CommonErrorCode.INVALID_INPUT;
		};
	}

}
