package com.readme.transcript;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.readme.common.error.ApiException;
import com.readme.llm.Llm;
import com.readme.llm.LlmErrorCode;
import com.readme.llm.LlmFeature;
import com.readme.transcript.TranscriptResponse.Course;

/**
 * 성적증명서의 글을 LLM 으로 과목 줄로 바꾸고(프롬프트 transcript-read.md), 값을 학점 계산기 문서 4절의 꼴로 맞춘다.
 * LLM 이 맞춰 주기를 믿지 않고 여기서 한 번 더 맞춘다: 이수구분 줄임말, 등급 표기, 학기 이름, 연도로만 적힌 학년.
 */
@Component
class TranscriptReader {

	/** 보내는 글의 상한. 성적증명서는 몇 쪽이고 몇천 자다. 넘으면 성적증명서로 보지 않고(NO_COURSES) 사용액도 아낀다 */
	static final int MAX_CHARS = 30_000;

	private static final String PROMPT = prompt();

	private static final Map<String, String> CATEGORIES = Map.of("전필", "전공필수", "전선", "전공선택", "교필", "교양필수", "교선",
			"교양선택", "일선", "일반선택");

	private static final Set<String> GRADES = Set.of("A+", "A0", "A-", "B+", "B0", "B-", "C+", "C0", "C-", "D+", "D0", "D-",
			"F", "P", "NP", "S", "U");

	private static final Pattern NUMBER = Pattern.compile("\\d+");

	/** 적힌 학년으로 믿는 범위의 끝(6년제 학과까지). 넘으면 학년을 읽지 못한 것으로 본다 */
	private static final int MAX_GRADE_YEAR = 6;

	/** 이보다 큰 학점은 확인 필요다 */
	private static final BigDecimal MAX_CREDIT = BigDecimal.TEN;

	private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");

	private final Llm llm;

	TranscriptReader(Llm llm) {
		this.llm = llm;
	}

	/** LLM 이 읽은 것(구조화 출력의 모양). 모든 칸이 글자이고 글에 없으면 "" 다. 틀린 값은 normalize 가 맞추거나 버린다. */
	record TranscriptAnswer(String school, String department, String scale, List<Row> courses) {

		/**
		 * @param year         학년. 적혀 있을 때만
		 * @param calendarYear 학년도나 연도. 학년이 없을 때 학년을 셈한다
		 */
		record Row(String year, String calendarYear, String term, String category, String name, String credit,
				String grade, String retake, List<String> unsure) {
		}

	}

	/**
	 * 한도는 부르는 쪽이 먼저 본다. Claude 가 답을 만들지 못하면 TRANSCRIPT_UNREADABLE, 과목 줄이 없거나 글이 MAX_CHARS 를 넘으면
	 * TRANSCRIPT_NO_COURSES 다. Claude 를 부를 수 없으면 503 LLM_UNAVAILABLE 그대로다.
	 */
	TranscriptResponse read(String text, long userId) {
		if (text.codePointCount(0, text.length()) > MAX_CHARS) {
			// 413 이면 화면이 "파일이 너무 큽니다"라고 한다. 파일은 작아도 글이 이만큼 길면 성적증명서가 아니다
			throw new ApiException(TranscriptErrorCode.TRANSCRIPT_NO_COURSES);
		}
		TranscriptAnswer answer;
		try {
			answer = this.llm.ask(userId, LlmFeature.TRANSCRIPT_READ, PROMPT, "[성적증명서 글]\n" + text,
					TranscriptAnswer.class);
		}
		catch (ApiException ex) {
			if (ex.errorCode() == LlmErrorCode.LLM_FAILED) {
				throw new ApiException(TranscriptErrorCode.TRANSCRIPT_UNREADABLE);
			}
			throw ex;
		}
		TranscriptResponse response = normalize(answer);
		if (response.courses().isEmpty()) {
			throw new ApiException(TranscriptErrorCode.TRANSCRIPT_NO_COURSES);
		}
		return response;
	}

	/** LLM 의 답을 응답 꼴로. 과목명이 빈 줄은 뺀다. */
	static TranscriptResponse normalize(TranscriptAnswer answer) {
		List<TranscriptAnswer.Row> rows = ((answer.courses() != null) ? answer.courses() : List.<TranscriptAnswer.Row>of())
			.stream()
			.filter((row) -> !text(row.name()).isEmpty())
			.toList();
		List<Integer> years = new ArrayList<>();
		List<Integer> calendarYears = new ArrayList<>();
		for (TranscriptAnswer.Row row : rows) {
			Integer year = number(row.year());
			Integer calendarYear = calendarYear(row.calendarYear());
			if (year != null && year >= 1900) { // LLM 이 학년 칸에 학년도를 넣었다
				calendarYear = (calendarYear != null) ? calendarYear : year;
				year = null;
			}
			years.add((year != null && year >= 1 && year <= MAX_GRADE_YEAR) ? year : null);
			calendarYears.add(calendarYear);
		}
		// 학년 없이 연도로만 적힌 성적증명서는 가장 이른 연도를 1학년으로 센다(문서 4절의 가정. 성적증명서는 연도 순이라 처음 나온 연도다)
		Integer firstYear = calendarYears.stream().filter((y) -> y != null).min(Integer::compare).orElse(null);
		List<Course> courses = new ArrayList<>();
		int previousYear = 1;
		for (int i = 0; i < rows.size(); i++) {
			TranscriptAnswer.Row row = rows.get(i);
			Integer year = years.get(i);
			if (year == null && calendarYears.get(i) != null) {
				year = calendarYears.get(i) - firstYear + 1;
			}
			if (year == null) {
				year = previousYear; // 학년도 연도도 없는 줄은 앞 줄의 학년이다(같은 학기 머리 아래에 있다)
			}
			previousYear = year;
			List<String> said = (row.unsure() != null) ? row.unsure() : List.of();
			BigDecimal credit = credit(row.credit());
			String grade = grade(row.grade());
			List<String> unsure = new ArrayList<>();
			// 10학점이 넘는 과목은 없다. 칸이 밀려 점수(95)가 학점으로 온 것일 수 있다
			if (credit == null || credit.compareTo(MAX_CREDIT) > 0 || said.contains("credit")) {
				unsure.add("credit");
			}
			if (!GRADES.contains(grade) || said.contains("grade")) {
				unsure.add("grade");
			}
			courses.add(new Course(year, term(row.term()), category(row.category()), text(row.name()),
					(credit != null) ? credit : BigDecimal.ZERO, grade, retake(row.retake()), unsure));
		}
		return new TranscriptResponse(orNull(answer.school()), orNull(answer.department()), scale(answer.scale()),
				courses);
	}

	/** 전필 → 전공필수 같은 줄임말을 푼다. 그 밖은 적힌 그대로. */
	static String category(String value) {
		String category = text(value);
		return CATEGORIES.getOrDefault(category, category);
	}

	/** 대문자와 기호로: a+ → A+, A·Ao → A0, 전각 기호 → 반각. 맞출 수 없으면 대문자로 바꾼 그대로. */
	static String grade(String value) {
		String grade = text(value).replaceAll("\\s", "")
			.toUpperCase(Locale.ROOT)
			.replace('＋', '+')
			.replace('－', '-')
			.replace('−', '-')
			.replace('–', '-');
		if (grade.matches("[A-D]O?")) {
			return grade.charAt(0) + "0";
		}
		return grade;
	}

	/** "1", "2", "여름", "겨울". 1학기·하계·동계 계절학기도 맞춘다. 그 밖은 적힌 그대로. */
	static String term(String value) {
		String term = text(value).replaceAll("\\s", "");
		if (term.contains("여름") || term.contains("하계")) {
			return "여름";
		}
		if (term.contains("겨울") || term.contains("동계")) {
			return "겨울";
		}
		if (term.matches("제?1(학기)?")) {
			return "1";
		}
		if (term.matches("제?2(학기)?")) {
			return "2";
		}
		return term;
	}

	/** 0 이상의 숫자. "3", "3.0", "3학점" → 3. 읽을 수 없으면 null. 응답에 3.0 이 아니라 3 으로 실리게 0 을 뗀다. */
	static BigDecimal credit(String value) {
		Matcher matcher = DECIMAL.matcher(text(value));
		if (!matcher.find()) {
			return null;
		}
		BigDecimal credit = new BigDecimal(matcher.group()).stripTrailingZeros();
		return (credit.scale() < 0) ? credit.setScale(0) : credit;
	}

	private static Double scale(String value) {
		BigDecimal scale = credit(value);
		if (scale == null) {
			return null;
		}
		double number = scale.doubleValue();
		return (number == 4.5 || number == 4.3) ? number : null;
	}

	/** 학년도·연도. 두 자리(19)는 2000년대로 본다. 1900 이전과 세 자리는 null. */
	private static Integer calendarYear(String value) {
		Integer year = number(value);
		if (year == null) {
			return null;
		}
		if (year < 100) {
			return 2000 + year;
		}
		return (year >= 1900) ? year : null;
	}

	private static String retake(String value) {
		String retake = text(value).toLowerCase(Locale.ROOT);
		return (retake.equals("before") || retake.equals("after")) ? retake : null;
	}

	/** 글자 속 첫 숫자. "2학년" → 2, "2019학년도" → 2019. 없으면 null. */
	private static Integer number(String value) {
		Matcher matcher = NUMBER.matcher(text(value));
		if (!matcher.find() || matcher.group().length() > 4) {
			return null;
		}
		return Integer.valueOf(matcher.group());
	}

	private static String text(String value) {
		return (value != null) ? value.strip() : "";
	}

	private static String orNull(String value) {
		String text = text(value);
		return text.isEmpty() ? null : text;
	}

	private static String prompt() {
		try (InputStream in = new ClassPathResource("prompts/transcript-read.md").getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프롬프트를 읽지 못했다: transcript-read.md", ex);
		}
	}

}
