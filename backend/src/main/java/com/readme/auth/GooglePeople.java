package com.readme.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

/**
 * 구글 People API 로 로그인한 사람의 생년월일·성별을 읽는다(로그인 설계 4.1, 프로필 방향 2절). 구글 동의 화면에서 허락한 범위의 칸만
 * 묻는다. 묻다가 실패하면(오류 응답, 시간 초과, 읽을 수 없는 답) 빈 값이다. 로그인을 막지 않고, 기록에는 토큰과 읽은 값을 남기지 않는다.
 */
@Component
class GooglePeople {

	static final String BIRTHDAY_SCOPE = "https://www.googleapis.com/auth/user.birthday.read";

	static final String GENDER_SCOPE = "https://www.googleapis.com/auth/user.gender.read";

	/** 로그인 응답이 이만큼 늦어질 수 있다. */
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	private static final Logger log = LoggerFactory.getLogger(GooglePeople.class);

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	private final String peopleUrl;

	GooglePeople(GoogleProperties properties) {
		this.peopleUrl = properties.peopleUrl();
	}

	/**
	 * @param birthDate 연·월·일이 모두 있는 생년월일. 없으면 null
	 * @param gender    MALE, FEMALE, null
	 */
	record Values(LocalDate birthDate, String gender) {

		static final Values NONE = new Values(null, null);

	}

	/**
	 * 구글이 준 토큰으로 읽는다. 사람이 두 범위를 모두 허락하지 않았으면(토큰의 scope 에 없으면) 묻지 않고 비어 있다.
	 * 하나라도 허락했으면 값을 담아 돌려준다. 묻다가 실패했으면 빈 값({@link Values#NONE})이다.
	 */
	Optional<Values> read(OAuth2AccessToken token) {
		boolean askBirthday = token.getScopes().contains(BIRTHDAY_SCOPE);
		boolean askGender = token.getScopes().contains(GENDER_SCOPE);
		if (!askBirthday && !askGender) {
			return Optional.empty();
		}
		String fields = (askBirthday && askGender) ? "birthdays,genders" : askBirthday ? "birthdays" : "genders";
		HttpRequest request = HttpRequest.newBuilder(URI.create(this.peopleUrl + "/v1/people/me?personFields=" + fields))
			.timeout(TIMEOUT)
			.header("Authorization", "Bearer " + token.getTokenValue())
			.GET()
			.build();
		try {
			HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				log.info("구글 People API 가 {} 로 답했다", response.statusCode());
				return Optional.of(Values.NONE);
			}
			JsonNode person = JsonMapper.shared().readTree(response.body());
			// 허락하지 않은 칸은 답에 있어도 쓰지 않는다
			return Optional.of(new Values(askBirthday ? birthDate(person.path("birthdays")) : null,
					askGender ? gender(person.path("genders")) : null));
		}
		catch (IOException | RuntimeException ex) {
			log.info("구글 People API 를 읽지 못했다: {}", ex.getClass().getSimpleName());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		return Optional.of(Values.NONE);
	}

	/**
	 * 대표(primary) 값부터 보아 연·월·일이 모두 있는 첫 날짜. 구글 계정의 생일(출처 ACCOUNT)은 연도가 있고, 프로필의 생일(PROFILE)은
	 * 연도를 숨긴 채 올 수 있다. 연도가 없으면 0 이거나 빠져 있다.
	 */
	static LocalDate birthDate(JsonNode birthdays) {
		for (JsonNode birthday : primaryFirst(birthdays)) {
			JsonNode date = birthday.path("date");
			int year = date.path("year").intValue(0);
			if (year > 0) {
				try {
					return LocalDate.of(year, date.path("month").intValue(0), date.path("day").intValue(0));
				}
				catch (DateTimeException ex) {
					// 월이나 일이 없거나 틀린 날짜는 건너뛴다
				}
			}
		}
		return null;
	}

	/** 대표(primary) 값 하나만 본다. male, female 말고(unspecified, 직접 적은 값)는 비워 둔다(프로필 설계 PR1). */
	static String gender(JsonNode genders) {
		return primaryFirst(genders).stream().findFirst().map((gender) -> switch (gender.path("value").stringValue("")) {
			case "male" -> "MALE";
			case "female" -> "FEMALE";
			default -> null;
		}).orElse(null);
	}

	private static List<JsonNode> primaryFirst(JsonNode values) {
		List<JsonNode> list = new ArrayList<>(values.values());
		list.sort(Comparator.comparing((JsonNode value) -> !value.path("metadata").path("primary").asBoolean(false)));
		return list;
	}

}
