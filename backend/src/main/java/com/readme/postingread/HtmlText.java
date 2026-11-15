package com.readme.postingread;

import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;

/**
 * HTML → 보이는 글자. 태그와 스크립트를 걷어내고 글자는 바꾸지 않는다. jobfit 의 src/jobfit/clean/html2text.py 를 옮겼다.
 *
 * <p>채용 사이트 실측으로 다듬어진 jobfit 의 규칙 그대로: li 는 "- " 글머리로, 표 칸은 " | "로, 제목 태그는 "#"으로 남겨 구조를
 * 글자로 보존한다. 그 밖의 블록 태그는 줄바꿈이 된다.
 *
 * <p>jobfit 과 다른 점: jobfit 은 정규식으로 태그를 지우고 리드미는 Jsoup 으로 문서를 읽은 뒤 같은 자리에 같은 글자를 넣는다.
 * 소스에 실제로 있던 태그에만 넣는다(Jsoup 이 채운 tbody, 생략된 &lt;/p&gt; 는 건너뛴다). 그래서 jobfit 이 태그로 잘못 보던
 * 글자 속 "&lt; … &gt;"가 남는다. 그 밖에는 같은 글자가 나온다(HtmlTextTests).
 */
final class HtmlText {

	/** 통째로 버리는 태그. 글자가 아니거나(스크립트), 화면의 버튼·선택지이거나, 다른 문서(iframe)다. */
	private static final Set<String> DROP = Set.of("script", "style", "noscript", "template", "svg", "head", "button", "select",
			"iframe");

	/** 폭 없는 공백. 사람인 제목에 섞여 온다(jobfit 의 meta.py 는 HTML 에서 먼저 지운다). */
	static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");

	private static final Set<String> BLOCKS = Set.of("p", "div", "tr", "section", "article", "dt", "dd", "dl", "table",
			"ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "header", "footer", "nav", "main", "aside", "form", "figure",
			"blockquote", "pre", "tbody", "thead");

	private static final Pattern SPACES = Pattern.compile("[ \\t\\r\\f\\u000B\\u00A0\\u3000]+"); // 전각 공백은 들여쓰기로 쓰인다

	private static final Pattern LINE_EDGES = Pattern.compile(" ?\\n ?");

	private static final Pattern EMPTY_BULLETS = Pattern.compile("\\n(?:[-|] ?\\n)+");

	private static final Pattern MANY_NL = Pattern.compile("\\n{3,}");

	/** {@code <li>ㆍ글</li>}, {@code <li>- 글</li>} 이 만든 "- ㆍ글", "- - 글"을 "- 글"로. */
	private static final Pattern DOUBLE_BULLET = Pattern.compile("^- [ㆍ•·▪-] ?", Pattern.MULTILINE | Pattern.UNIX_LINES);

	private HtmlText() {
	}

	static String toText(String html) {
		return toText(parse(html), false);
	}

	/** 소스의 태그 자리를 기억하며 읽는다. 한 페이지는 한 번만 읽고 글자, 필수값, iframe 을 모두 이 문서에서 찾는다. */
	static Document parse(String html) {
		return Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true));
	}

	/**
	 * 문서의 보이는 글자. 문서는 바꾸지 않는다(버리는 태그는 건너뛴다).
	 *
	 * @param dropZeroWidth 폭 없는 공백을 지운 HTML 을 읽은 것과 같은 글자로. jobfit 의 meta.py 가 요약 칸을 읽는 글자다
	 */
	static String toText(Document document, boolean dropZeroWidth) {
		StringBuilder out = new StringBuilder();
		NodeTraversor.filter(new NodeFilter() {

			@Override
			public FilterResult head(Node node, int depth) {
				if (node instanceof TextNode text) {
					out.append(text.getWholeText());
				}
				else if (node instanceof Element element) {
					if (DROP.contains(element.normalName())) {
						return FilterResult.SKIP_ENTIRELY;
					}
					if (!element.sourceRange().isImplicit()) {
						out.append(opening(element.normalName()));
					}
				}
				return FilterResult.CONTINUE;
			}

			@Override
			public FilterResult tail(Node node, int depth) {
				if (node instanceof Element element && BLOCKS.contains(element.normalName())
						&& element.endSourceRange().isTracked() && !element.endSourceRange().isImplicit()) {
					out.append('\n');
				}
				return FilterResult.CONTINUE;
			}

		}, document);
		String text = SPACES.matcher(dropZeroWidth ? ZERO_WIDTH.matcher(out).replaceAll("") : out).replaceAll(" ");
		text = LINE_EDGES.matcher(text).replaceAll("\n");
		text = EMPTY_BULLETS.matcher(text).replaceAll("\n");
		text = MANY_NL.matcher(text).replaceAll("\n\n");
		text = DOUBLE_BULLET.matcher(text).replaceAll("- ");
		return Py.strip(text);
	}

	private static String opening(String tag) {
		return switch (tag) {
			case "br" -> "\n";
			case "li" -> "\n- ";
			case "td", "th" -> " | ";
			case "h1", "h2", "h3", "h4", "h5", "h6" -> "\n" + "#".repeat(tag.charAt(1) - '0') + " ";
			default -> BLOCKS.contains(tag) ? "\n" : "";
		};
	}

}
