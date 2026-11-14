package com.readme.postingread;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.readme.common.config.TimeConfig;
import com.readme.posting.DeadlineKind;
import com.readme.postingread.Deadlines.Read;

/**
 * 공고 페이지에서 회사명·직무·마감일 읽기. jobfit 의 src/jobfit/fetch/meta.py 를 옮겼다.
 *
 * <p>순서: JSON-LD JobPosting(잡코리아 등) → 사이트별 제목 규칙(사람인·점핏·잡코리아의 og:title) → 못 읽음. 마감일을 못 읽었으면
 * 요약 칸(마감일, 접수마감, 접수기간)에서 읽는다. 못 읽으면 빈칸으로 둔다. 추측하지 않는다.
 *
 * <p>jobfit 과 다른 점(PostingMetaTests):
 * <ul>
 * <li>채용 솔루션 페이지에 박힌 데이터(그리팅·나인하이어·리크루터, jobfit 의 company.py)는 아직 읽지 않는다. 그 단계를 건너뛴다.</li>
 * <li>JSON-LD 의 validThrough 에 시간대가 붙어 있으면 서울 날짜로 바꾼다. jobfit 은 앞 10자(UTC 날짜)를 쓴다.</li>
 * <li>마감은 {@link Deadlines} 로 읽는다(상시, 연도 없는 날짜).</li>
 * <li>값마다 어디서 읽었는지(근거)를 함께 돌려준다.</li>
 * </ul>
 */
final class PostingMeta {

	private static final int U = Pattern.UNICODE_CHARACTER_CLASS;

	/** 사람인 제목에 폭 없는 공백이 섞여 온다. */
	private static final Pattern ZW = HtmlText.ZERO_WIDTH;

	private static final Pattern CORP = Pattern.compile("㈜|\\(\\s*주\\s*\\)|\\(\\s*유\\s*\\)|주식회사|유한회사", U);

	private static final String RUN_START = PostingSections.RUN_START;

	/** ㈜예시컴퍼니(example) 의 (example). */
	private static final Pattern PAREN_LATIN = Pattern.compile(RUN_START + "\\s*\\([A-Za-z0-9 .&,'’-]+\\)\\s*", U);

	/** [시리즈A투자유치] */
	private static final Pattern LEADING_TAGS = Pattern.compile("^(\\s*\\[[^\\]]*\\]\\s*)+", U);

	private static final Pattern DDAY = Pattern.compile(RUN_START + "\\s*\\((D-\\d+|D-day|오늘\\s*마감|마감\\s*임박)\\)",
			U | Pattern.CASE_INSENSITIVE);

	private static final Pattern SITE_SUFFIX = Pattern.compile(
			RUN_START + "\\s*[-|–—]\\s*(사람인|잡코리아|점핏|원티드|리멤버|프로그래머스|Wanted|JobKorea|Saramin)\\s*$",
			U | Pattern.CASE_INSENSITIVE);

	/** JSON-LD 의 회사명과 공고명은 칸의 길이(postings.company 200자, title 300자)까지만 읽는다. 더 길면 읽지 못한 것으로 본다. */
	private static final int MAX_COMPANY = 200;

	private static final int MAX_TITLE = 300;

	/** 사람인·점핏: "[(주)예시랩] 백엔드 (Backend) 개발자(D-7)" */
	private static final Pattern BRACKET_TITLE = Pattern.compile("^\\[([^\\]]+)\\]\\s*(.+)$", U);

	/** 잡코리아: "㈜예시컴퍼니(example) 채용 - [시리즈A투자유치] Node.js개발자(신입)" */
	private static final Pattern JOBKOREA_TITLE = Pattern.compile("^(.+?)\\s*채용\\s*[-–—]\\s*(.+)$", U);

	private static final Pattern WHITESPACE = Pattern.compile("\\s+", U);

	private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}");

	/** 요약 칸에서 마감을 찾는 순서. */
	private static final List<String> DEADLINE_KEYS = List.of("마감일", "접수마감", "접수기간");

	private PostingMeta() {
	}

	/**
	 * 페이지에서 읽은 값. 못 읽은 칸은 "", 근거(…From)는 그 값을 읽은 원문 조각이다.
	 *
	 * @param source    "json-ld", "title", 못 읽었으면 ""
	 * @param pageTitle 페이지의 &lt;title&gt;
	 */
	record Meta(String company, String companyFrom, String title, String titleFrom, Read deadline, String deadlineFrom,
			String source, String pageTitle) {
	}

	/** 요약 칸에서 읽은 마감과 그 근거("마감일 2026.10.27 23:59"). */
	record Dated(Read deadline, String from) {

		static final Dated NONE = new Dated(Read.UNKNOWN, "");

	}

	static Meta read(String html, LocalDate today) {
		return read(HtmlText.parse(html), today);
	}

	/**
	 * 읽은 문서에서. jobfit 은 HTML 에서 폭 없는 공백을 먼저 지우고, 리드미는 문서에서 꺼낸 글자마다 지운다(같은 값이 나온다).
	 * 문서는 바꾸지 않는다.
	 */
	static Meta read(Document document, LocalDate today) {
		Meta meta = fromJsonLd(document);
		if (meta == null) {
			meta = fromPageTitle(document);
		}
		if (meta == null) {
			meta = new Meta("", "", "", "", Read.UNKNOWN, "", "", "");
		}
		Dated deadline = meta.deadline().known() ? new Dated(meta.deadline(), meta.deadlineFrom())
				: deadlineFromSummary(HtmlText.toText(document, true), today);
		return new Meta(meta.company(), meta.companyFrom(), meta.title(), meta.titleFrom(), deadline.deadline(),
				deadline.from(), meta.source(), noZeroWidth(document.title()));
	}

	/** 요약 칸의 마감일·접수마감·접수기간 가운데 처음 읽히는 것. */
	static Dated deadlineFromSummary(String text, LocalDate today) {
		Map<String, String> fields = PostingSections.summaryFields(text);
		for (String key : DEADLINE_KEYS) {
			String value = fields.get(key);
			if (value != null) {
				Read deadline = Deadlines.parse(value, today);
				if (deadline.known()) {
					return new Dated(deadline, key + " " + value);
				}
			}
		}
		return Dated.NONE;
	}

	static String cleanCompany(String name) {
		String s = CORP.matcher(ZW.matcher(name).replaceAll("")).replaceAll("");
		s = PAREN_LATIN.matcher(s).replaceAll(" ");
		return Py.strip(WHITESPACE.matcher(s).replaceAll(" "));
	}

	static String cleanJobTitle(String title) {
		String s = SITE_SUFFIX.matcher(ZW.matcher(title).replaceAll("")).replaceAll("");
		s = DDAY.matcher(s).replaceAll("");
		s = LEADING_TAGS.matcher(s).replaceAll("");
		return Py.strip(WHITESPACE.matcher(s).replaceAll(" "));
	}

	// ── 내부 ──────────────────────────────────────────────────────────

	private static Meta fromJsonLd(Document document) {
		for (Element script : document.select("script[type*=application/ld+json]")) {
			JsonNode root;
			try {
				root = JsonMapper.shared().readTree(noZeroWidth(script.data()));
			}
			catch (JacksonException ex) {
				continue;
			}
			for (JsonNode item : walk(root, new ArrayList<>())) {
				if (!isJobPosting(item.get("@type"))) {
					continue;
				}
				JsonNode org = item.get("hiringOrganization");
				String company = upTo(MAX_COMPANY, unescape((org != null && org.isObject()) ? text(org.get("name")) : text(org)));
				String title = upTo(MAX_TITLE, unescape(text(item.get("title"))));
				String valid = text(item.get("validThrough"));
				Read deadline = validThrough(valid);
				Meta meta = new Meta(cleanCompany(company), company, cleanJobTitle(title), title, deadline,
						deadline.known() ? valid : "", "json-ld", "");
				if (!meta.company().isEmpty()) {
					return meta;
				}
			}
		}
		return null;
	}

	private static List<JsonNode> walk(JsonNode node, List<JsonNode> out) {
		if (node.isArray()) {
			node.values().forEach((child) -> walk(child, out));
		}
		else if (node.isObject()) {
			out.add(node);
			if (node.has("@graph")) {
				walk(node.get("@graph"), out);
			}
		}
		return out;
	}

	private static boolean isJobPosting(JsonNode type) {
		if (type == null) {
			return false;
		}
		if (type.isArray()) {
			return type.values().stream().anyMatch((t) -> t.isString() && "JobPosting".equals(t.stringValue()));
		}
		return type.isString() && "JobPosting".equals(type.stringValue());
	}

	/** Python 의 str(값 or ""): 없거나 비었으면 "", 글자면 그대로, 숫자 등은 글자로. */
	private static String text(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return "";
		}
		return node.isValueNode() ? node.asString() : node.toString();
	}

	/** validThrough. 시간대가 붙어 있으면 서울 날짜로, 없으면 앞의 날짜 그대로. */
	private static Read validThrough(String valid) {
		if (!ISO_DATE.matcher(valid).find()) {
			return Read.UNKNOWN;
		}
		try {
			return new Read(DeadlineKind.DATE, OffsetDateTime.parse(valid).atZoneSameInstant(TimeConfig.SEOUL).toLocalDate());
		}
		catch (DateTimeParseException ex) {
			// 시간대 없는 "2026-11-15T23:59"
		}
		try {
			return new Read(DeadlineKind.DATE, LocalDate.parse(valid.substring(0, 10)));
		}
		catch (DateTimeParseException ex) {
			return Read.UNKNOWN;
		}
	}

	private static Meta fromPageTitle(Document document) {
		Element og = document.selectFirst("meta[property=og:title]");
		Element title = document.selectFirst("title");
		String raw;
		if (og != null && !noZeroWidth(og.attr("content")).isEmpty()) {
			raw = Py.strip(WHITESPACE.matcher(noZeroWidth(og.attr("content"))).replaceAll(" "));
		}
		else if (title != null) {
			raw = Py.strip(WHITESPACE.matcher(noZeroWidth(title.text())).replaceAll(" "));
		}
		else {
			return null;
		}
		Matcher site = SITE_SUFFIX.matcher(raw);
		if (!site.find()) {
			return null; // 모르는 사이트의 제목은 회사·직무 경계를 알 수 없다
		}
		String name = site.group(1);
		String body = Py.strip(SITE_SUFFIX.matcher(raw).replaceAll(""));
		Matcher bracket = BRACKET_TITLE.matcher(body);
		if (bracket.find() && List.of("사람인", "점핏", "Saramin").contains(name)) {
			return titled(bracket.group(1), bracket.group(2), raw);
		}
		Matcher jobkorea = JOBKOREA_TITLE.matcher(body);
		if (jobkorea.find()) {
			return titled(jobkorea.group(1), jobkorea.group(2), raw);
		}
		return null;
	}

	private static Meta titled(String company, String title, String raw) {
		return new Meta(cleanCompany(company), raw, cleanJobTitle(title), raw, Read.UNKNOWN, "", "title", "");
	}

	private static String unescape(String text) {
		return Parser.unescapeEntities(text, false);
	}

	private static String noZeroWidth(String text) {
		return ZW.matcher(text).replaceAll("");
	}

	/** 칸보다 긴 값은 "". jobfit 은 길이를 보지 않는다. */
	private static String upTo(int max, String value) {
		return (Py.len(value) > max) ? "" : value;
	}

}
