package com.readme.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** People API 의 답에서 생년월일·성별 고르기(로그인 설계 4.1). Spring 없이 돈다. */
class GooglePeopleTests {

	@Test
	void theBirthDateIsTheFirstFullDatePrimaryFirst() {
		// 프로필의 생일(대표)은 연도를 숨겼고, 구글 계정의 생일에 연도가 있다
		assertThat(GooglePeople.birthDate(json("""
				[ { "metadata": { "source": { "type": "ACCOUNT" } }, "date": { "year": 1999, "month": 3, "day": 1 } },
				  { "metadata": { "primary": true, "source": { "type": "PROFILE" } }, "date": { "month": 3, "day": 1 } } ]
				"""))).isEqualTo(LocalDate.of(1999, 3, 1));
		assertThat(GooglePeople.birthDate(json("""
				[ { "date": { "year": 2001, "month": 1, "day": 2 } },
				  { "metadata": { "primary": true }, "date": { "year": 1999, "month": 3, "day": 1 } } ]
				"""))).isEqualTo(LocalDate.of(1999, 3, 1));
	}

	@Test
	void aBirthdayWithoutAYearOrAValidDateIsSkipped() {
		assertThat(GooglePeople.birthDate(json("""
				[ { "date": { "year": 0, "month": 3, "day": 1 } }, { "date": { "month": 3, "day": 1 } },
				  { "date": { "year": 1999, "month": 2, "day": 30 } }, { "date": { "year": 1999, "month": 0, "day": 0 } },
				  { "text": "1999-03-01" } ]
				"""))).isNull();
		assertThat(GooglePeople.birthDate(json("[]"))).isNull();
		assertThat(GooglePeople.birthDate(json("{}").path("birthdays"))).isNull();
	}

	@Test
	void theGenderIsThePrimaryValueWhenItIsMaleOrFemale() {
		assertThat(GooglePeople.gender(json("""
				[ { "value": "female" }, { "metadata": { "primary": true }, "value": "male" } ]
				"""))).isEqualTo("MALE");
		assertThat(GooglePeople.gender(json("[ { \"value\": \"female\" } ]"))).isEqualTo("FEMALE");
		// 밝히지 않음, 직접 적은 값은 비워 둔다. 대표 값이 그렇다면 다른 값을 찾지 않는다
		assertThat(GooglePeople.gender(json("""
				[ { "metadata": { "primary": true }, "value": "unspecified" }, { "value": "male" } ]
				"""))).isNull();
		assertThat(GooglePeople.gender(json("[ { \"value\": \"nonbinary\" } ]"))).isNull();
		assertThat(GooglePeople.gender(json("[ { \"value\": \"Male\" } ]"))).isNull();
		assertThat(GooglePeople.gender(json("{}").path("genders"))).isNull();
	}

	private static JsonNode json(String text) {
		return JsonMapper.shared().readTree(text);
	}

}
