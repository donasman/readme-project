package com.readme.transcript;

import java.math.BigDecimal;
import java.util.List;

/**
 * 읽은 성적증명서(학점 계산기 문서 4절). 칸 이름과 값은 그 문서와 프런트의 타입이 같게 둔다.
 *
 * @param school     학교. 못 읽으면 null
 * @param department 학과. 못 읽으면 null
 * @param scale      적힌 만점 4.5 나 4.3. 없거나 다른 값이면 null
 * @param courses    성적증명서에 나온 순서. 비지 않았다
 */
record TranscriptResponse(String school, String department, Double scale, List<Course> courses) {

	/**
	 * 과목 한 줄.
	 *
	 * @param year     학년(1부터)
	 * @param term     "1", "2", "여름", "겨울". 그 밖이면 적힌 그대로
	 * @param category 이수구분. 줄임말은 풀었다. 없으면 ""
	 * @param credit   학점. 못 읽었으면 0 이고 unsure 에 "credit" 이 있다
	 * @param grade    A+, A0, A- … F, P, NP, S, U. 그 밖이면(못 읽었으면 "") unsure 에 "grade" 가 있다
	 * @param retake   "before", "after", null
	 * @param unsure   확실히 읽지 못한 칸: "credit", "grade"
	 */
	record Course(int year, String term, String category, String name, BigDecimal credit, String grade, String retake,
			List<String> unsure) {
	}

}
