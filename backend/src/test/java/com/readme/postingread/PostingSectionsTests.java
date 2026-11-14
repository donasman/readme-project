package com.readme.postingread;

import static com.readme.postingread.HtmlTextTests.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.readme.postingread.PostingSections.Section;

/**
 * 공고 글자의 구조(본문 유무, 섹션, 요약 칸). jobfit 의 tests/test_clean.py 에서 sections.py 를 보는 것을 옮겼고, 아래쪽 값은
 * jobfit 의 sections.py 를 python -I 로 직접 돌려 얻었다.
 *
 * <p>옮기지 않은 것: test_jobkorea_summary_box_comes_from_the_meta_description(clean_job 과 jobfit 의 파일 저장을 거친다.
 * 리드미는 잡코리아 마감일을 JSON-LD 에서 읽는다), test_keywords_do_not_overmatch(키워드 사전, 다른 브랜치가 옮긴다),
 * test_mask_contacts(LLM 프롬프트에 넣을 때 쓴다), test_find_questions_*(자소서 문항 찾기. 공고 읽어 오기에 쓰지 않는다).
 */
class PostingSectionsTests {

	// ── test_clean.py ──────────────────────────────────────────────────

	@Test
	void saraminDetailHasBodyAndSections() {
		String text = PostingSections.dropNoise(HtmlText.toText(resource("saramin-sample-detail.html")));
		assertThat(PostingSections.hasPostingBody(text)).isTrue();
		assertThat(names(text)).containsAnyOf("자격요건", "주요업무");
	}

	@Test
	void saraminSummaryFieldsTwoLineLayout() {
		// 사람인 요약 칸은 "경력" 줄, 빈 줄, "신입·경력 3~15년" 줄로 온다
		Map<String, String> fields = PostingSections.summaryFields(
				PostingSections.dropNoise(HtmlText.toText(resource("saramin-sample.html"))));
		assertThat(fields).containsEntry("경력", "신입·경력 3~15년").containsEntry("근무형태", "정규직");
		assertThat(fields.get("마감일")).startsWith("2026.10.27");
	}

	@Test
	void jobkoreaSummaryIsNotOnTheScreen() {
		assertThat(PostingSections.summaryFields(PostingSections.dropNoise(HtmlText.toText(resource("jobkorea-sample.html")))))
			.isEmpty();
	}

	@Test
	void joinedSummaryLineIsSplitOnlyBeforeAKey() {
		// "대학졸업(2,3년)이상" 의 쉼표에서 자르면 값이 깨진다. 뒤에 요약 키가 오는 쉼표에서만 자른다
		assertThat(PostingSections.summaryFromJoined("경력 : 신입 , 학력 : 대학졸업(2,3년)이상, 급여 : 회사내규에 따름"))
			.containsExactly(Map.entry("경력", "신입"), Map.entry("학력", "대학졸업(2,3년)이상"), Map.entry("급여", "회사내규에 따름"));
		assertThat(PostingSections.summaryFromJoined("회사 소개만 있는 설명문입니다")).isEmpty();
	}

	@Test
	void jobkoreaDetailSectionsAndBracketHeaders() {
		String text = PostingSections.dropNoise(HtmlText.toText(resource("jobkorea-sample-detail.html")));
		assertThat(PostingSections.hasPostingBody(text)).isTrue();
		List<String> names = names(text);
		assertThat(names).contains("주요업무", "우대사항", "접수방법", "근무조건", "전형절차", "복리후생", "회사소개");
		assertThat(names.stream().filter("자격요건"::equals).count()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void summarySingleLineLayouts() {
		assertThat(PostingSections.summaryFields("| 경력 | 신입 |\n학력: 대졸\n근무지역：서울 강남구"))
			.containsExactly(Map.entry("경력", "신입"), Map.entry("학력", "대졸"), Map.entry("근무지역", "서울 강남구"));
	}

	@Test
	void headerRulesSeparateHeadersFromContent() {
		String text = String.join("\n", "| 담당 업무", "ㆍPython 기반 API 개발", "| 필수 경험과 역량", "ㆍBackend 업무 경험이 3년 이상이신 분",
				"ㆍ근무요일 : 주 5일(월-금)", "| 우대 사항", "ㆍDocker 경험", "처우 협의 (평판조회)");
		List<Section> sections = PostingSections.split(text);
		assertThat(sections).extracting(Section::name).containsExactly("주요업무", "자격요건", "우대사항");
		assertThat(sections.get(1).text()).contains("근무요일 : 주 5일(월-금)"); // 값 줄은 헤더가 아니라 내용
		assertThat(sections.get(2).text()).contains("처우 협의 (평판조회)");
	}

	@Test
	void menuOnlyTextIsNotBody() {
		String menu = String.join("\n", List.of("로그인", "회원가입", "채용정보", "공채속보", "이력서", "고객센터", "이용약관")).repeat(30);
		assertThat(PostingSections.hasPostingBody(menu)).isFalse();
	}

	@Test
	void bodyGroupsCountOnlyOnShortLines() {
		// test_company.py 의 것. 실측(CJ 채용) 동의문과 같은 꼴·길이로, 항목을 한 줄에 길게 늘어놓는다
		String consent = "예시그룹(이하 회사)은 개인정보 보호법 등 관련 법령에 따라 채용 과정 진행 및 채용 후 인사관리 등의 목적으로 입사지원자의 개인정보를 수집하고 있습니다.\n"
				+ "- 성명(한글/영문), 성별, 생년월일, 국적, 주소, 휴대전화번호, 지원경로, 이메일, 학력사항(학력, 졸업학교명, 학교소재지, 전공, 성적), "
				+ "경력사항(회사명, 근무기간, 고용형태, 직급, 담당업무, 퇴직사유)";
		assertThat(PostingSections.hasPostingBody(consent)).isFalse();
		assertThat(PostingSections.hasPostingBody("## 담당업무\n- 주문 API 개발\n## 자격요건\n- Python 3년\n" + consent)).isTrue();
	}

	// ── sections.py 를 직접 돌려 얻은 값 ───────────────────────────────────

	@Test
	void fixtureSectionsAndHeadersAreTheSameAsJobfit() {
		String saramin = PostingSections.dropNoise(HtmlText.toText(resource("saramin-sample-detail.html")));
		assertThat(PostingSections.split(saramin)).extracting(Section::name, Section::header)
			.containsExactly(tuple("회사소개", "회사 소개"), tuple("모집부문", "모집부문"), tuple("주요업무", "담당 업무"),
					tuple("자격요건", "필요 스택"), tuple("자격요건", "필수 경험과 역량"), tuple("우대사항", "우대 사항"),
					tuple("전형절차", "전형절차"), tuple("근무조건", "근무조건"), tuple("접수방법", "접수기간 및 방법"));
		String jobkorea = PostingSections.dropNoise(HtmlText.toText(resource("jobkorea-sample-detail.html")));
		assertThat(PostingSections.split(jobkorea)).extracting(Section::name, Section::header)
			.containsExactly(tuple("머리", ""), tuple("회사소개", "㈜예시컴퍼니(example)를 소개해요"), tuple("주요업무", "이런 업무를 해요"),
					tuple("자격요건", "이런 분들을 찾고 있어요"), tuple("우대사항", "이런 분이면 더 좋아요"), tuple("근무조건", "근무 조건"),
					tuple("접수방법", "지원 방법 및 절차"), tuple("자기소개서", "자기소개서 작성 요령"), tuple("자격요건", "이런 기술이 필요해요"),
					tuple("근무조건", "이런 조건에서 근무할 예정이에요"), tuple("복리후생", "함께하면 이런 점들이 좋아요"),
					tuple("전형절차", "합류 여정은 이렇게 진행돼요"), tuple("접수방법", "지원 시 유의해주세요"));
		// 요약 페이지는 본문이 아니다. 요약 칸 셋(근무형태·급여·근무지역)이 근무조건 헤더로 잡힌다
		String summary = PostingSections.dropNoise(HtmlText.toText(resource("saramin-sample.html")));
		assertThat(PostingSections.hasPostingBody(summary)).isFalse();
		assertThat(PostingSections.split(summary)).extracting(Section::name, Section::header)
			.containsExactly(tuple("머리", ""), tuple("근무조건", "근무형태"), tuple("근무조건", "급여"), tuple("근무조건", "근무지역"));
	}

	@Test
	void saraminSummaryFieldsAreTheSameAsJobfit() {
		String summary = PostingSections.dropNoise(HtmlText.toText(resource("saramin-sample.html")));
		assertThat(PostingSections.summaryFields(summary)).containsExactly(Map.entry("경력", "신입·경력 3~15년"),
				Map.entry("학력", "대졸(2,3년제) 이상"), Map.entry("근무형태", "정규직"), Map.entry("급여", "면접 후 결정"),
				Map.entry("근무지역", "서울 강남구, 서울전체"), Map.entry("마감일", "2026.10.27 23:59"));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			"마감일\\n\\n2026.10.27 23:59"           | 마감일   | 2026.10.27 23:59
			"마감일 : ~11/20(목) 23:59"               | 마감일   | ~11/20(목) 23:59
			"접수기간 | 2026.11.01 ~ 2026.11.20"      | 접수기간 | 2026.11.01 ~ 2026.11.20
			"마감일\\n경력\\n신입"                     | 경력     | 신입
			"마감일: 상시채용"                         | 마감일   | 상시채용
			""")
	void summaryValuesAreTheSameAsJobfit(String text, String key, String value) {
		assertThat(PostingSections.summaryFields(text.replace("\\n", "\n"))).containsExactly(Map.entry(key, value));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			백엔드 - 사람인    | 백엔드
			"백엔드 | 잡코리아" | 백엔드
			백엔드 — Wanted   | 백엔드
			백엔드-사람인x     | 백엔드-사람인x
			""")
	void cleanTitleIsTheSameAsJobfit(String title, String expected) {
		assertThat(PostingSections.cleanTitle(title)).isEqualTo(expected);
	}

	@Test
	void cleanTitleDropsTrailingSiteNameAfterColon() {
		assertThat(PostingSections.cleanTitle("백엔드 : 점핏 ")).isEqualTo("백엔드");
	}

	/** 공백 5만 칸(보통 공백과 em 공백)도 바로 끝난다. 괄호 머리 정규식이 공백 수의 제곱만큼 걸리던 것을 막았다. */
	@ParameterizedTest
	@ValueSource(strings = { " ", " " })
	void longRunsOfSpacesTakeLinearTime(String space) {
		String run = space.repeat(50_000);
		String text = String.join("\n", "주요 업무", "[" + run + "x" + run + "]", "x" + run + "]", "마감일 :" + run + "x", "자격 요건",
				"- Java" + run + "3년");
		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
			assertThat(PostingSections.split(text)).extracting(Section::name).containsExactly("주요업무", "자격요건");
			assertThat(PostingSections.cleanTitle("x" + run + "x" + run + "- 사람인")).isEqualTo("x" + run + "x");
			assertThat(PostingSections.dropNoise(text)).isEqualTo(text);
			assertThat(PostingSections.hasPostingBody(text)).isTrue();
			assertThat(PostingSections.summaryFields(text)).containsExactly(Map.entry("마감일", "x"));
		});
	}

	private static List<String> names(String text) {
		return PostingSections.split(text).stream().map(Section::name).toList();
	}

}
