package com.readme.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.readme.keyword.Numbers.NumToken;

/**
 * 숫자 뽑기. jobfit 의 tests/test_numbers.py 를 옮겼고(위쪽), 아래 표는 jobfit 의 numbers.py 를 Python 3.12 로 직접 돌려
 * 얻은 값이다(리드미 사전의 영문 표기를 버전 이름으로 넘겼다).
 *
 * <p>pytest 는 버전 이름으로 Java, Spring Boot, Python, Node.js 를 넘기는데, 넷 다 기본 목록에 있어 넘기지 않아도 같다.
 * 옮기지 않은 것: test_severity(초안 검사의 등급. 아직 쓰는 곳이 없다).
 */
class NumbersTests {

	// ── test_numbers.py ─────────────────────────────────────────────────

	@Test
	void sameQuantityInManySpellings() {
		List<NumToken> tokens = Stream.of("약 3천만 건", "삼천만 건", "30,000,000건", "3,000만 건")
			.map((text) -> Numbers.extract(text).numbers().get(0))
			.toList();
		assertThat(tokens).extracting(NumToken::value, NumToken::unit).containsOnly(tuple(30_000_000.0, "건"));
		assertThat(Numbers.sameValue(tokens.get(0), tokens.subList(1, 4))).isTrue();
	}

	@Test
	void unitsAndMultipliers() {
		assertThat(values("p95 1.8초 → 0.4초, 쿼리 21회 → 1회"))
			.containsExactly(tuple(1.8, "초"), tuple(0.4, "초"), tuple(21.0, "회"), tuple(1.0, "회"));
		assertThat(values("비용 50만 원 절감, 3배 빨라짐, 40% 감소"))
			.containsExactly(tuple(500_000.0, "원"), tuple(3.0, "배"), tuple(40.0, "%"));
		assertThat(values("2인 팀, 3번 배포, 6개월")).containsExactly(tuple(2.0, "인"), tuple(3.0, "회"), tuple(6.0, "개월"));
	}

	@Test
	void namesDatesMarkersAreNotNumbers() {
		Numbers.Extracted ex = Numbers.extract(
				"Java 17과 Spring Boot 3, S3·EC2·OAuth2, HTTP/2, p95. 2025년 3월부터 2025.06까지. [확인 필요: 사용자 수 1만 명]\n1. 첫째");
		assertThat(ex.numbers()).isEmpty();
		assertThat(ex.years()).containsExactly(2025, 2025);
	}

	@Test
	void koreanNumeralNeedsUnitAndWordStart() {
		assertThat(Numbers.korToInt("삼천만")).isEqualTo(30_000_000);
		assertThat(Numbers.korToInt("천만")).isEqualTo(10_000_000);
		assertThat(Numbers.korToInt("이십")).isEqualTo(20);
		assertThat(Numbers.korToInt("만약")).isZero();
		assertThat(values("천만 원")).containsExactly(tuple(10_000_000.0, "원"));
		assertThat(values("수천만 건의 데이터")).isEmpty(); // 어림말은 숫자로 보지 않는다
		assertThat(values("만들었습니다 일주일 만에")).isEmpty();
	}

	@Test
	void datesTimesRelationsAreNotNumbers() {
		Numbers.Extracted ex = Numbers
			.extract("2026.09.17 실측, 2026-10-28 기준, 09-15 측정, 2026 – 현재, 매일 04:00(KST) 백업, 1:N 상세 · N:M 관계 · 1:1 멘토링");
		assertThat(ex.numbers()).isEmpty();
		assertThat(ex.years()).containsExactly(2026, 2026, 2026);
		assertThat(values("2025년 3개월 동안")).containsExactly(tuple(3.0, "개월")); // 연도 뒤의 기간은 남긴다
		assertThat(values("2025년 3월 5일")).isEmpty();
	}

	@Test
	void httpStatusCodesAreNotNumbers() {
		for (String text : List.of("429 오류가 났다", "응답이 200이어도 실패로 처리", "HTTP 500", "네트워크 오류 · 429 · 5xx만 재시도",
				"429는 원인이 달랐다", "동시 저장 500을 막음")) {
			assertThat(values(text)).as(text).isEmpty();
		}
		assertThat(values("약 500 백엔드 테스트")).containsExactly(tuple(500.0, "")); // 오류 문맥이 아니면 수량
		assertThat(values("429 원인 분석, 04 단계에서 멈춤, 01~07 단계")).isEmpty(); // 429와 앞자리 0은 이름
		assertThat(values("429건, 05분")).containsExactly(tuple(429.0, "건"), tuple(5.0, "분")); // 단위가 붙으면 수량
		assertThat(values("오류 300건 감소")).containsExactly(tuple(300.0, "건"));
	}

	@Test
	void versionsWithoutProjectDictionary() {
		assertThat(Numbers.extract("PostgreSQL 17, React 18, Next.js 16, Spring Boot 2.6, Gemini 3.5 Flash, Prisma 7, JDK 11")
			.numbers()).isEmpty();
		assertThat(values("Java 3년 경력, React 18개 컴포넌트")).containsExactly(tuple(3.0, "년"), tuple(18.0, "개"));
	}

	@Test
	void suffixesRangesAndMixedKorean() {
		assertThat(values("~700k 토큰, 41k, 1.2k")).containsExactly(tuple(700_000.0, ""), tuple(41_000.0, ""), tuple(1_200.0, ""));
		assertThat(values("60t 동시 입고, 8t 중 5t이")).containsExactly(tuple(60.0, "t"), tuple(8.0, "t"), tuple(5.0, "t"));
		assertThat(values("약 4만 3천 콜, 1억 2천만 원, 3만 5000원"))
			.containsExactly(tuple(43_000.0, ""), tuple(120_000_000.0, "원"), tuple(35_000.0, "원"));
		assertThat(values("응답 압축 45~78%, 3~6회"))
			.containsExactly(tuple(45.0, "%"), tuple(78.0, "%"), tuple(3.0, "회"), tuple(6.0, "회"));
		assertThat(Numbers.sameValue(first("43,000 콜"), Numbers.extract("약 4만 3천 콜").numbers())).isTrue();
	}

	@Test
	void wordsThatStartWithAUnitAreNotUnits() {
		assertThat(values("약 500 백엔드 테스트")).containsExactly(tuple(500.0, "")); // 백엔드의 '백'은 100이 아니다
		assertThat(values("3만큼")).containsExactly(tuple(3.0, ""));
		assertThat(values("순위표 6대회, OAuth 2 인증")).containsExactly(tuple(6.0, ""));
		assertThat(values("3년간, 5명의, 12개월간, 3주치, 0.4초로, 3년차")).containsExactly(tuple(3.0, "년"), tuple(5.0, "명"),
				tuple(12.0, "개월"), tuple(3.0, "주"), tuple(0.4, "초"), tuple(3.0, "년"));
	}

	@Test
	void sameValueRules() {
		List<NumToken> facts = Numbers.extract("p95 1.8초 → 0.4초, 6GB").numbers();
		assertThat(Numbers.sameValue(first("1.8초"), facts)).isTrue();
		assertThat(Numbers.sameValue(first("1.8"), facts)).isTrue(); // 단위 없는 쪽은 값만 본다
		assertThat(Numbers.sameValue(first("1.8분"), facts)).isFalse(); // 단위가 다르면 다른 값
		assertThat(Numbers.sameValue(first("78%"), facts)).isFalse(); // 계산해서 만든 비율
	}

	// ── numbers.py 를 직접 돌려 얻은 값 ─────────────────────────────────────

	static Stream<Arguments> sameNumbersAsJobfit() {
		return Stream.of(
				// 쉼표, 소수, 단위 배수, 한글 숫자
				row("매출 1,234,567원", n(1234567, "원", "1,234,567원")),
				row("1,234.5건", n(1234.5, "건", "1,234.5건")),
				row("12.5% 개선, 0.5배", n(12.5, "%", "12.5%"), n(0.5, "배", "0.5배")),
				row("3천만 원, 3,000만 원, 약 3천만 건", n(30000000, "원", "3천만 원"), n(30000000, "원", "3,000만 원"),
						n(30000000, "건", "3천만 건")),
				row("삼천오백만 원", n(35000000, "원", "삼천오백만 원")),
				row("오천 명", n(5000, "명", "오천 명")),
				row("4만 3천 5백 원", n(43500, "원", "4만 3천 5백 원")),
				row("1억 5000만 원", n(150000000, "원", "1억 5000만 원")),
				row("백만 원, 십 명, 이십 명", n(1000000, "원", "백만 원"), n(20, "명", "이십 명")),
				row("주 3회, 3주 동안, 하루 2만 건", n(3, "회", "3회"), n(3, "주", "3주"), n(20000, "건", "2만 건")),
				// 섞인 한글 숫자는 큰 단위가 앞이어야 한다. 거꾸로면 숫자로 보지 않는다
				row("4만 3천, 1억 2천만", n(43000, "", "4만 3천"), n(120000000, "", "1억 2천만")),
				row("3만 2억 원"),
				// 범위: 뒤 숫자의 단위를 앞 숫자도 갖는다. 물결표와 줄표(–), 사이의 공백
				row("10~20%, 1,000~2,000건, 3–5회, 5 ~ 10 명", n(10, "%", "10%"), n(20, "%", "20%"), n(1000, "건", "1,000건"),
						n(2000, "건", "2,000건"), n(3, "회", "3회"), n(5, "회", "5회"), n(5, "명", "5명"), n(10, "명", "10명")),
				row("45 ~ 78 %", n(45, "%", "45%"), n(78, "%", "78%")),
				row("2배~3배", n(2, "배", "2배"), n(3, "배", "3배")),
				// k, 단위의 다른 이름(퍼센트, 프로, 번, 달, 밀리초, 톤), 전각 글자(NFKC)
				row("10k, 1.5K 요청", n(10000, "", "10k"), n(1500, "", "1.5K")),
				row("５０％ 감소, ３０만 원", n(50, "%", "50%"), n(300000, "원", "30만 원")),
				row("50 퍼센트, 30프로, 3번, 2달, 500밀리초, 60톤", n(50, "%", "50 퍼센트"), n(30, "%", "30프로"), n(3, "회", "3번"),
						n(2, "개월", "2달"), n(500, "ms", "500밀리초"), n(60, "t", "60톤")),
				row("100GB, 1.5TB, 512MB", n(100, "GB", "100GB"), n(1.5, "TB", "1.5TB"), n(512, "MB", "512MB")),
				row("3 kg, 60t를", n(3, "kg", "3 kg"), n(60, "t", "60t")),
				row("3배 이상, 2.5배", n(3, "배", "3배"), n(2.5, "배", "2.5배")),
				// '여'는 단위가 아니다
				row("10여 명, 5천여 명", n(10, "", "10"), n(5000, "", "5천")),
				// 버전: 사전의 영문 표기(리드미가 더한 MES, OPC UA 포함) 뒤 숫자
				row("Java 17로 3년", n(3, "년", "3년")),
				row("MES 2 도입, OPC UA 1.04 연동"),
				// 날짜, 줄 머리 번호, [확인 필요]
				row("2025.03 ~ 2025.06 동안 40% 개선", n(40, "%", "40%")),
				row("1. 첫째 7건\n2) 둘째 8건", n(7, "건", "7건"), n(8, "건", "8건")),
				row("[확인 필요: 30%] 줄였다"),
				// 응답 시간처럼 단위가 붙으면 HTTP 문맥이어도 수량이다
				row("응답 시간 200ms → 50ms", n(200, "ms", "200ms"), n(50, "ms", "50ms")),
				row("HTTP 200 응답 비율 99.9%", n(99.9, "%", "99.9%")),
				row("500 에러 0건", n(0, "건", "0건")),
				// 숫자 앞뒤의 글자
				row("1,23명", n(1, "", "1")),
				row(".5초"),
				row("1.2.3 버전", n(1.2, "", "1.2")),
				row("5G 망, 2x 빨라짐"),
				row("+30%, -5%", n(30, "%", "30%"), n(5, "%", "5%")),
				row("1/3", n(1, "", "1"), n(3, "", "3")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource
	void sameNumbersAsJobfit(String text, List<NumToken> expected) {
		assertThat(Numbers.extract(text).numbers()).containsExactlyElementsOf(expected);
	}

	@Test
	void yearsAndKoreanNumeralsSameAsJobfit() {
		assertThat(Numbers.extract("2025.03 ~ 2025.06 동안 40% 개선").years()).containsExactly(2025, 2025);
		assertThat(Numbers.korToInt("삼천오백만")).isEqualTo(35000000);
		assertThat(Numbers.korToInt("구십구")).isEqualTo(99);
		assertThat(Numbers.korToInt("일억이천만")).isEqualTo(120000000);
		assertThat(Numbers.korToInt("만")).isEqualTo(10000);
		assertThat(Numbers.korToInt("억")).isEqualTo(100000000);
		assertThat(Numbers.korToInt("십만")).isEqualTo(100000);
		assertThat(Numbers.korToInt("이백오십")).isEqualTo(250);
	}

	@Test
	void sameValueSameAsJobfit() {
		assertThat(Numbers.sameValue(first("1,000건"), Numbers.extract("1천 건").numbers())).isTrue();
		assertThat(Numbers.sameValue(first("30%"), Numbers.extract("30").numbers())).isTrue();
		assertThat(Numbers.sameValue(first("30"), Numbers.extract("30%").numbers())).isTrue();
		assertThat(Numbers.sameValue(first("2배"), Numbers.extract("2").numbers())).isTrue();
		assertThat(Numbers.sameValue(first("3명"), Numbers.extract("3인").numbers())).isFalse(); // 명과 인은 다른 단위다
	}

	private static List<Tuple> values(String text) {
		return Numbers.extract(text).numbers().stream().map((n) -> tuple(n.value(), n.unit())).toList();
	}

	private static NumToken first(String text) {
		return Numbers.extract(text).numbers().get(0);
	}

	private static NumToken n(double value, String unit, String raw) {
		return new NumToken(value, unit, raw);
	}

	private static Arguments row(String text, NumToken... numbers) {
		return Arguments.of(text, List.of(numbers));
	}

}
