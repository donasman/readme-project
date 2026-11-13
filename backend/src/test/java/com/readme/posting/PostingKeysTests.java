package com.readme.posting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 공고키. jobfit 의 tests/test_jobkey.py 를 옮겼고(위쪽 여섯 개), 아래 표는 jobfit 의 jobkey.py 를 Python 3.13 으로 직접 돌려
 * 얻은 값이다. Python 의 urlsplit·parse_qsl·urlencode 와 같은 키가 나오는지 본다.
 *
 * <p>옮기지 않은 것: test_kind_board_company_local(게시처 판정. 아직 쓰는 곳이 없다),
 * test_dirname_is_windows_safe(jobfit 의 로컬 폴더 이름. 리드미에는 없다).
 */
class PostingKeysTests {

	// ── test_jobkey.py ──────────────────────────────────────────────────

	@Test
	void saraminRelayAndViewShareKey() {
		PostingKey a = PostingKeys.derive("https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=10000001&utm_source=x", "", "", "");
		PostingKey b = PostingKeys.derive("https://m.saramin.co.kr/zf_user/jobs/view?rec_idx=10000001", "", "", "");
		assertThat(a.key()).isEqualTo("saramin:10000001").isEqualTo(b.key());
		assertThat(a.site()).isEqualTo("saramin");
		assertThat(a.number()).isEqualTo("10000001");
	}

	@Test
	void jobkoreaTwoUrlShapes() {
		assertThat(key("https://www.jobkorea.co.kr/Recruit/GI_Read/10000002?Oem_Code=C1")).isEqualTo("jobkorea:10000002");
		assertThat(key("https://www.jobkorea.co.kr/Recruit/GI_Read_Comt_Ifrm?Gno=10000002")).isEqualTo("jobkorea:10000002");
	}

	@Test
	void wantedJumpitProgrammers() {
		assertThat(key("https://www.wanted.co.kr/wd/123456")).isEqualTo("wanted:123456");
		assertThat(key("https://jumpit.saramin.co.kr/position/9876")).isEqualTo("jumpit:9876");
		assertThat(key("https://www.jumpit.co.kr/position/9876")).isEqualTo("jumpit:9876");
		assertThat(key("https://career.programmers.co.kr/job_positions/555")).isEqualTo("programmers:555");
	}

	@Test
	void tossNeedsBothIds() {
		assertThat(key("https://toss.im/career/job-detail?job_id=1000000003&sub_position_id=1")).isEqualTo("toss:1000000003-1");
		assertThat(key("https://toss.im/career/job-detail?job_id=1000000003")).startsWith("url:toss.im/career/job-detail");
	}

	@Test
	void unknownSiteStripsTrackingAndFragment() {
		PostingKey k = PostingKeys.derive("https://www.example.com/careers/42/?fbclid=abc&utm_medium=social&page=2#apply", "", "", "");
		assertThat(k.key()).isEqualTo("url:example.com/careers/42?page=2");
		assertThat(k.site()).isEqualTo("url");
	}

	@Test
	void noUrlUsesCompanyTitleDeadline() {
		PostingKey a = PostingKeys.derive("", "예시컴퍼니", "Node.js개발자", "2026-11-15");
		PostingKey b = PostingKeys.derive("", "예시컴퍼니", "Node.js개발자", "2026-11-15");
		PostingKey c = PostingKeys.derive("", "예시컴퍼니", "Node.js개발자", "2026-12-01");
		assertThat(a.key()).isEqualTo(b.key()).isNotEqualTo(c.key());
		assertThat(a.site()).isEqualTo("local");
		assertThat(a.hasUrl()).isFalse();
	}

	// ── jobkey.py 를 직접 돌려 얻은 값 ─────────────────────────────────────

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
			// 쿼리: 순서를 지키고, 추적값을 빼고, 빈 값과 = 없는 이름은 "이름="이 된다. 경로 끝의 / 는 모두 뗀다
			"https://Example.COM/a/b//?b=2&a=1&ref=x&t_ref_x=1&utm_source=y&empty=&flag | url:example.com/a/b?b=2&a=1&empty=&flag=",
			// 퍼센트 인코딩을 풀었다가 다시 인코딩한다(공백은 +)
			"https://m.example.com/jobs?q=%ED%95%9C%EA%B8%80+%EA%B0%9C%EB%B0%9C&x=a%2Bb | url:example.com/jobs?q=%ED%95%9C%EA%B8%80+%EA%B0%9C%EB%B0%9C&x=a%2Bb",
			// 인코딩하지 않은 한글·공백도 같은 키가 된다. * 는 인코딩하고 ~ 는 둔다
			"https://example.com/jobs?q=한글 개발&star=*&tilde=~ | url:example.com/jobs?q=%ED%95%9C%EA%B8%80+%EA%B0%9C%EB%B0%9C&star=%2A&tilde=~",
			// 포트는 남기고 # 뒤는 뺀다
			"https://jobs.example.com:8443/path/?k=v#frag | url:jobs.example.com:8443/path?k=v",
			// 스킴이 없으면 호스트를 읽지 못해 사람인 규칙도 맞지 않는다(jobfit 과 같다)
			"www.saramin.co.kr/zf_user/jobs/view?rec_idx=5 | url:www.saramin.co.kr/zf_user/jobs/view?rec_idx=5",
			// 잘못된 % 는 글자로 두고, 깨진 UTF-8 은 대체 문자가 된다
			"https://example.com/a?%zz=1&b=%E0%A4 | url:example.com/a?%25zz=1&b=%EF%BF%BD",
			// ; 는 구분자가 아니다. 빈 칸(&&)은 건너뛴다
			"https://example.com/a?a=1;b=2&&c | url:example.com/a?a=1%3Bb%3D2&c=",
			// 앞뒤 공백은 뗀다
			"\"  https://www.wanted.co.kr/wd/777?utm_source=x  \" | wanted:777",
			"https://remember.co.kr/posting/31 | remember:31",
			"https://example.com | url:example.com" })
	void sameKeysAsJobfit(String url, String expected) {
		assertThat(key(url)).isEqualTo(expected);
	}

	@Test
	void localKeysSameAsJobfit() {
		assertThat(PostingKeys.derive("", "예시컴퍼니", "Node.js개발자", "2026-11-15").key()).isEqualTo("local:779642d2");
		assertThat(PostingKeys.derive("", " 회사 ", "직무", "").key()).isEqualTo("local:45e4427e");
		assertThat(PostingKeys.derive(null, " 회사 ", "직무", null).key()).isEqualTo("local:45e4427e");
	}

	private static String key(String url) {
		return PostingKeys.derive(url, "", "", "").key();
	}

}
