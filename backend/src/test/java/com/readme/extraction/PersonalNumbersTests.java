package com.readme.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 자료 본문의 주민번호·전화번호 가리기(프로필 방향 문서 3절 5번). 경험카드에 쓰는 숫자(날짜, 기간, 금액, 비율, 버전)는 남는다.
 */
class PersonalNumbersTests {

	@ParameterizedTest
	@ValueSource(strings = { "950101-1234567", "950101 - 2234567", "001231-4234567", "850315-5123456",
			// 붙여 쓴 13자리, Word 가 바꿔 넣은 대시, 전각 하이픈
			"9501011234567", "950101–1234567", "950101－1234567" })
	void residentRegistrationNumbersAreMasked(String number) {
		assertThat(PersonalNumbers.mask("주민등록번호: " + number + " (만 30세)")).isEqualTo("주민등록번호: [주민번호] (만 30세)");
	}

	@ParameterizedTest
	@ValueSource(strings = { "010-1234-5678", "010.1234.5678", "010 1234 5678", "01012345678", "010 - 1234 - 5678",
			"010–1234–5678", "010－1234－5678", "011-123-4567", "016-1234-5678", "019-123-4567",
			// 유선: 서울, 지역, 괄호 친 국번, 인터넷 전화, 평생번호
			"02-123-4567", "02-1234-5678", "021234567", "(02) 1234-5678", "(031)123-4567", "0311234567", "051-123-4567",
			"064-123-4567", "070-1234-5678", "0505-123-4567",
			// 국가 번호
			"+82-10-1234-5678", "+82 10 1234 5678", "+821012345678", "+82 (0)10-1234-5678", "+82-010-1234-5678",
			"+82-2-1234-5678" })
	void phoneNumbersAreMasked(String number) {
		assertThat(PersonalNumbers.mask("연락처 " + number + " 입니다")).isEqualTo("연락처 [전화번호] 입니다");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2025-03-01", "2023.03 ~ 2024.02", "2023.03.01 ~ 2024.02.29", "2021.02 - 2023.02",
			"2019-02-01 ~ 2023-02-28", "2024.02.15 14:30", "02:30", "1,234,567원", "매출 35% 증가", "p95 1.8초 → 0.4초",
			"Spring Boot 3.2.1", "v1.10.2", "192.168.0.10", "학번 20191234", "우편번호 06236", "02-15",
			// 대표번호는 회사의 번호이고 범위와 꼴이 같다
			"1588-1234", "1500-2000명",
			// 덜 쓴 번호, 앞뒤에 숫자가 더 붙은 번호
			"010-1234", "주문번호 010123456789", "1010-1234-5678", "019501011234567",
			// 날짜가 아닌 앞자리, 성별 자리가 아닌 뒷자리, 이미 가린 주민번호(생년월일과 성별만 남았다)
			"951301-1234567", "950132-1234567", "950101-9234567", "950101-12345678", "950101-1******" })
	void datesAmountsVersionsAndOtherNumbersStay(String text) {
		assertThat(PersonalNumbers.mask(text)).isEqualTo(text);
	}

	@Test
	void everyNumberInAResumeIsMaskedAndTheRestStays() {
		String resume = """
				홍길동(010-1234-5678) / daon@example.com
				주민등록번호 950101-1234567
				2023.03 ~ 2024.02 예시 커머스, 주문 조회 API p95 1.8초 → 0.4초
				""";

		assertThat(PersonalNumbers.mask(resume)).isEqualTo("""
				홍길동([전화번호]) / daon@example.com
				주민등록번호 [주민번호]
				2023.03 ~ 2024.02 예시 커머스, 주문 조회 API p95 1.8초 → 0.4초
				""");
	}

}
