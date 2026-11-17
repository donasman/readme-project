package com.readme.postingread;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * HTML → 보이는 글자. jobfit 의 tests/test_clean.py 에서 html_to_text 를 보는 둘을 옮겼고, 아래 표와 픽스처 글자는
 * jobfit 의 html2text.py 를 python -I 로 직접 돌려 얻은 값이다.
 *
 * <p>픽스처(resources/postingread/*.html)는 jobfit 의 tests/fixtures 에 있는 합성 페이지(가상의 회사)를 그대로 옮겼다.
 * 같은 이름의 .txt 는 jobfit 이 만든 drop_noise(html_to_text(…)) 결과다.
 */
class HtmlTextTests {

	// ── test_clean.py ──────────────────────────────────────────────────

	@Test
	void keepsStructureAndEntities() {
		String html = "<div><h2>자격요건</h2><ul><li>Java &amp; Spring</li><li>3년&nbsp;이상</li></ul><table><tr><td>경력</td><td>신입</td></tr></table><script>x()</script></div>";
		String text = HtmlText.toText(html);
		assertThat(text).contains("## 자격요건", "- Java & Spring", "- 3년 이상", "| 경력 | 신입").doesNotContain("x()");
	}

	@Test
	void fullwidthSpaceAndNestedBullets() {
		assertThat(HtmlText.toText("<ul><li>　- 지원동기: 알려주세요</li><li>ㆍ학력 : 대졸</li></ul>"))
			.isEqualTo("- 지원동기: 알려주세요\n- 학력 : 대졸");
	}

	// ── html2text.py 를 직접 돌려 얻은 값 ───────────────────────────────────

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			<p>a &amp; b&nbsp;c</p><p>&lt;b&gt;x&lt;/b&gt;</p> | "a & b c\\n\\n<b>x</b>"
			<div>  line1 <br> line2<br/>line3 </div>            | "line1\\nline2\\nline3"
			<ul><li></li><li> </li><li>x</li></ul>               | "- x"
			<h1>T</h1><h3>sub</h3>text                           | "# T\\n\\n### sub\\ntext"
			<table><tr><th>a</th><td>b</td></tr><tr><td></td></tr></table> | "| a | b"
			<p>a</p><p></p><p></p><p></p><p>b</p>                 | "a\\n\\nb"
			<ul><li>ㆍ가</li><li>- 나</li><li>• 다</li><li>·라</li><li>▪마</li></ul> | "- 가\\n- 나\\n- 다\\n- 라\\n- 마"
			<span>a</span><span>b</span> <b>c</b>                | "ab c"
			text only                                            | "text only"
			<P>Upper</P><BR><LI>x                                | "Upper\\n\\n- x"
			<div>a<div>b</div>c</div>                            | "a\\nb\\nc"
			""")
	void sameTextAsJobfit(String html, String expected) {
		assertThat(HtmlText.toText(html)).isEqualTo(expected.replace("\\n", "\n"));
	}

	@Test
	void dropsScriptsAndControlsLikeJobfit() {
		String html = "<!-- c --><p>a</p><script>var x = '<p>';</script><style>p{}</style><noscript>n</noscript><template>t</template>"
				+ "<svg><text>s</text></svg><button>btn</button><select><option>o</option></select><iframe src=x>i</iframe>b";
		assertThat(HtmlText.toText(html)).isEqualTo("a\nb");
	}

	@Test
	void collapsesIndentSpacesTabsAndCarriageReturns() {
		assertThat(HtmlText.toText("<p>　　들여쓰기  끝</p>")).isEqualTo("들여쓰기 끝");
		assertThat(HtmlText.toText("<p>tab\there</p>")).isEqualTo("tab here");
		assertThat(HtmlText.toText("<p>a\r\nb</p>")).isEqualTo("a\nb");
	}

	@ParameterizedTest
	@ValueSource(strings = { "saramin-sample", "saramin-sample-detail", "jobkorea-sample", "jobkorea-sample-detail" })
	void fixturesReadTheSameAsJobfit(String name) {
		String text = PostingSections.dropNoise(HtmlText.toText(resource(name + ".html")));
		assertThat(text).isEqualTo(resource(name + ".txt"));
	}

	// ── jobfit 과 다른 점: 정규식이 아니라 Jsoup 으로 태그를 읽는다 ───────────────────

	@Test
	void anglesInsideTextStayText() {
		// jobfit 은 <[^>]+> 를 지워 "3 < 5 > 1"의 "< 5 >"를 태그로 보고 지운다("3 1"). 엔티티로 쓴 것은 둘 다 남긴다
		assertThat(HtmlText.toText("<p>a &lt; b and c &gt; d</p><p>3 < 5 > 1</p>")).isEqualTo("a < b and c > d\n\n3 < 5 > 1");
	}

	@Test
	void impliedEndTagsAddNoLines() {
		// 소스에 없는 닫는 태그(</p>)와 소스에 없는 tbody 는 줄을 더하지 않는다. jobfit 의 정규식이 보는 것과 같다
		assertThat(HtmlText.toText("<p>a<div>b</div>")).isEqualTo("a\nb");
		assertThat(HtmlText.toText("x<table><tr><td>a</td></tr></table>")).isEqualTo("x\n\n| a");
	}

	static String resource(String name) {
		try (InputStream in = HtmlTextTests.class.getResourceAsStream("/postingread/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
