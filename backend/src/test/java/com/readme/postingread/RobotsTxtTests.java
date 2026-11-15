package com.readme.postingread;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * robots.txt 읽기. jobfit 의 src/jobfit/fetch/robots.py 는 Python 표준 라이브러리 urllib.robotparser 에 맡기므로, 값은
 * robotparser 를 python -I(3.13.14)로 직접 돌려 얻었다. 판정은 RobotFileParser.parse(…)와 can_fetch(UA, 주소)다.
 *
 * <p>Python 3.13 의 robotparser 는 RFC 9309 를 따른다: 가장 긴 규칙이 이기고, 길이가 같으면 Allow 가 이기고, * 와 끝의 $ 를 읽고,
 * 같은 이름의 그룹은 합치고, /robots.txt 는 늘 받을 수 있다. jobfit 의 가상 환경(3.12)은 예전 규칙(먼저 맞는 줄이 이긴다)이다.
 * 리드미는 RFC 9309 쪽을 옮겼다.
 */
class RobotsTxtTests {

	private static final String UA = "ReadmeFetcher/1.0 (+https://app.readme.example)";

	private static final Map<String, String> FILES = Map.ofEntries(
			Map.entry("longest", "User-agent: *\nDisallow: /jobs\nAllow: /jobs/view\n"),
			Map.entry("tie_allow", "User-agent: *\nDisallow: /a\nAllow: /a\n"),
			Map.entry("wildcard", "User-agent: *\nDisallow: /*.pdf$\nDisallow: /search*q=\nAllow: /\n"),
			Map.entry("dollar", "User-agent: *\nDisallow: /exact$\n"),
			Map.entry("ours", "User-agent: *\nDisallow: /\n\nUser-agent: readmefetcher\nAllow: /\nDisallow: /private\n"),
			Map.entry("substring", "User-agent: fetcher\nDisallow: /\n"),
			Map.entry("merge", "User-agent: a\nUser-agent: readmefetcher\nDisallow: /x\n\nUser-agent: readmefetcher\nDisallow: /y\n"),
			Map.entry("empty_disallow", "User-agent: *\nDisallow:\n"),
			Map.entry("comments", "# c\nUser-agent: * # all\nDisallow: /p # private\n"),
			Map.entry("no_group", "Disallow: /\n"),
			Map.entry("encoded", "User-agent: *\nDisallow: /%EC%B1%84%EC%9A%A9\nDisallow: /a%2fb\n"),
			Map.entry("korean", "User-agent: *\nDisallow: /채용\n"),
			Map.entry("query", "User-agent: *\nDisallow: /view?rec_idx=\n"),
			Map.entry("ua_no_rules", "User-agent: readmefetcher\n\nUser-agent: *\nDisallow: /\n"),
			Map.entry("bad_dollar", "User-agent: *\nDisallow: /a$b\nDisallow: /c\n"),
			Map.entry("crlf", "User-agent: *\r\nDisallow: /r\r\n"),
			Map.entry("star_only", "User-agent: *\nDisallow: *\n"),
			Map.entry("double_star", "User-agent: *\nDisallow: /a**b\n"));

	@ParameterizedTest(name = "{0} {1} → {2}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			# 가장 긴 규칙이 이긴다
			longest        | /jobs                  | false
			longest        | /jobs/view?rec_idx=1   | true
			longest        | /jobs/list             | false
			longest        | /                      | true
			# 길이가 같으면 Allow
			tie_allow      | /a                     | true
			# * 와 끝의 $
			wildcard       | /file.pdf              | false
			wildcard       | /file.pdf?x=1          | true
			# 규칙 쪽의 = 는 %3D 로 바뀌어 주소의 q= 와 맞지 않는다(Python 과 같다)
			wildcard       | /search?q=java         | true
			dollar         | /exact                 | false
			dollar         | /exact/more            | true
			# 우리 이름의 그룹이 * 보다 먼저다
			ours           | /                      | true
			ours           | /private/x             | false
			# 그룹 이름이 우리 이름(readmefetcher)에 들어 있으면 우리 그룹이다
			substring      | /                      | false
			substring      | /robots.txt            | true
			# 같은 이름의 그룹은 합친다
			merge          | /x                     | false
			merge          | /y                     | true
			merge          | /z                     | true
			empty_disallow | /a                     | true
			comments       | /p                     | false
			comments       | /private/x             | false
			comments       | /x                     | true
			# User-agent 앞의 규칙은 버린다
			no_group       | /                      | true
			# 퍼센트 인코딩은 풀었다 다시 해서 비교한다
			encoded        | /채용                   | false
			encoded        | /%EC%B1%84%EC%9A%A9/1  | false
			encoded        | /a/b                   | false
			encoded        | /a%2Fb                 | false
			korean         | /채용                   | false
			korean         | /%EC%B1%84%EC%9A%A9/1  | false
			korean         | /a%2Fb                 | true
			query          | /view?rec_idx=5        | false
			query          | /view                  | true
			# 빈 줄은 그룹을 끊지 않는다. 두 이름이 한 그룹이 되어 Disallow: / 를 함께 받는다
			ua_no_rules    | /                      | false
			# 가운데의 $ 는 규칙이 아니다(그 줄만 버린다)
			bad_dollar     | /a$b                   | true
			bad_dollar     | /c                     | false
			crlf           | /r                     | false
			star_only      | /                      | false
			star_only      | /robots.txt            | true
			double_star    | /a/b                   | false
			double_star    | /aXYZb                 | false
			double_star    | /ab                    | false
			double_star    | /a$b                   | false
			double_star    | /A                     | true
			longest        | ""                     | true
			""")
	void sameAsPythonRobotparser(String file, String target, boolean allowed) {
		assertThat(RobotsTxt.parse(FILES.get(file)).allows(UA, target)).isEqualTo(allowed);
	}

}
