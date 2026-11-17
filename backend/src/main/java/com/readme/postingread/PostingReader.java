package com.readme.postingread;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.nodes.Document;

import org.springframework.stereotype.Component;

import com.readme.common.error.ApiException;
import com.readme.keyword.KeywordDictionary;
import com.readme.postingread.PageFetcher.Page;
import com.readme.postingread.PostingMeta.Dated;
import com.readme.postingread.PostingMeta.Meta;
import com.readme.postingread.PostingReadResponse.Found;
import com.readme.postingread.PostingReadResponse.SectionValue;
import com.readme.postingread.PostingReadResponse.Source;
import com.readme.postingread.PostingSections.Section;

/**
 * 링크나 붙여넣은 본문에서 공고를 읽는다. 규칙만 쓴다(LLM 없음). 규칙으로 못 읽은 칸은 unread 로 알린다.
 * jobfit 의 clean/posting.py 가 받은 페이지와 붙여넣은 글을 다루는 순서를 따른다.
 */
@Component
class PostingReader {

	/** 붙여넣은 글의 첫 줄 "[회사] 공고명". 사람인·점핏 제목과 같은 꼴이다(jobfit 은 붙여넣은 글에서 회사·공고명을 읽지 않는다). */
	private static final Pattern BRACKET_TITLE = Pattern.compile("^\\[([^\\]]+)\\]\\s*(.+)$", Pattern.UNICODE_CHARACTER_CLASS);

	/** 묶음 항목 앞의 글머리. ※ 는 안내라서 남긴다. */
	private static final Pattern ITEM_MARK = Pattern.compile("^(?:[-ㆍ•·▪■□●○◆◇▶▷*>]\\s*)+", Pattern.UNICODE_CHARACTER_CLASS);

	/** jobfit 의 섹션 이름 → 화면의 묶음 이름(예시 데이터와 프런트의 가짜 서버가 쓰는 이름). */
	private static final Map<String, String> SECTION_TITLES = Map.ofEntries(Map.entry("모집부문", "모집 부문"),
			Map.entry("주요업무", "주요 업무"), Map.entry("자격요건", "필수 요건"), Map.entry("우대사항", "우대 사항"),
			Map.entry("인재상", "인재상"), Map.entry("자기소개서", "자기소개서"), Map.entry("전형절차", "전형 절차"),
			Map.entry("근무조건", "근무 조건"), Map.entry("복리후생", "복리후생"), Map.entry("회사소개", "회사 소개"),
			Map.entry("접수방법", "접수 방법"));

	private static final int FIRST_LINE_MAX = 120;

	/** 키워드를 찾는 섹션(jobfit 의 섹션 이름): 주요 업무, 필수 요건, 우대 사항. */
	private static final Set<String> KEYWORD_SECTIONS = Set.of("주요업무", "자격요건", "우대사항");

	/** 키워드 수. jobfit 의 공고 분석이 공고마다 남기는 키워드 수(analyze.py 의 LIST_LIMITS)와 같다. */
	static final int MAX_KEYWORDS = 15;

	/** 한 서버에서 한 번에 읽는 링크 수. */
	static final int MAX_READS = 3;

	private final PageFetcher fetcher;

	private final Semaphore reads = new Semaphore(MAX_READS);

	PostingReader(PageFetcher fetcher) {
		this.fetcher = fetcher;
	}

	/**
	 * 붙여넣은 본문, 또는 파일에서 읽은 글(PostingReadController). 글은 고치지 않는다(jobfit 의 _clean_pasted). 본문이 없어 보여도 막지 않는다. 사용자가 본문이라고 준 것이다.
	 *
	 * @param sourceUrl 원문 링크. 없으면 null
	 */
	PostingReadResponse fromText(String text, String sourceUrl, LocalDate today) {
		String clean = Py.strip(text.replace("\r\n", "\n").replace('\r', '\n'));
		String first = firstLine(clean);
		Found company = Found.NONE;
		Found title = Found.NONE;
		Matcher bracket = BRACKET_TITLE.matcher(first);
		if (bracket.find()) {
			company = new Found(PostingMeta.cleanCompany(bracket.group(1)), first);
			title = new Found(PostingMeta.cleanJobTitle(bracket.group(2)), first);
		}
		Dated deadline = PostingMeta.deadlineFromSummary(clean, today);
		List<Section> split = PostingSections.split(clean);
		return PostingReadResponse.read(company, title, deadline.deadline(), deadline.from(),
				(sourceUrl != null) ? sourceUrl.strip() : "", sections(split), keywords(split, clean), new Source(first, clean));
	}

	/**
	 * 링크. 사이트 규칙대로 요약 페이지를 받고, 거기에 공고 본문이 없으면 같은 공고의 상세요강(또는 같은 호스트 iframe 하나)을 받는다.
	 * 둘 다 본문이 없으면 422 FETCH_FAILED 다(로그인 필요, 스크립트로 그리는 화면, 그림 공고).
	 *
	 * <p>한 번에 MAX_READS 개까지만 읽는다. 큰 페이지를 읽은 문서는 메모리를 많이 쓰고(1MB 의 빈 태그만으로 수십 MB), 느린 사이트는
	 * 요청 스레드를 오래 잡는다. 넘으면 기다리지 않고 422 FETCH_FAILED 다.
	 * shortcut: 서버 한 대 안에서만 센다, 서버를 여러 대 띄우거나 읽기가 자주 막히면 수를 다시 정하거나 줄을 세운다.
	 */
	PostingReadResponse fromUrl(String url, LocalDate today) {
		if (!this.reads.tryAcquire()) {
			throw new ApiException(PostingReadErrorCode.FETCH_FAILED,
					"지금 읽고 있는 링크가 많습니다. 잠시 뒤 다시 시도하거나 공고 본문을 복사해 붙여넣어 주세요");
		}
		try {
			return read(url, today);
		}
		finally {
			this.reads.release();
		}
	}

	private PostingReadResponse read(String url, LocalDate today) {
		FetchPlans.Plan plan = FetchPlans.plan(url);
		MainPage main = readMain(this.fetcher.html(plan.mainUrl(), plan.referer()), plan, today);
		String mainText = main.text();
		String detailText = "";
		if (!PostingSections.hasPostingBody(mainText)) {
			Optional<String> detail = plan.detailUrls().stream().findFirst().or(main::iframe);
			if (detail.isPresent()) {
				detailText = PostingSections.dropNoise(HtmlText.toText(this.fetcher.html(detail.get(), plan.mainUrl()).html()));
			}
		}
		String body = (!detailText.isEmpty() && PostingSections.hasPostingBody(detailText)) ? detailText : mainText;
		if (!PostingSections.hasPostingBody(body)) {
			throw new ApiException(PostingReadErrorCode.FETCH_FAILED,
					"공고 본문을 찾지 못했습니다. 로그인이 필요하거나, 화면을 스크립트로 그리거나, 그림으로 된 공고일 수 있습니다. 공고 본문을 복사해 붙여넣어 주세요");
		}
		Meta meta = main.meta();
		Dated deadline = meta.deadline().known() ? new Dated(meta.deadline(), meta.deadlineFrom())
				: PostingMeta.deadlineFromSummary(detailText, today);
		String full = Stream.of(mainText, detailText).filter((t) -> !t.isEmpty()).collect(Collectors.joining("\n\n"));
		String pageTitle = !meta.pageTitle().isEmpty() ? meta.pageTitle() : firstLine(detailText.isEmpty() ? mainText : detailText);
		List<Section> split = PostingSections.split(body);
		return PostingReadResponse.read(new Found(meta.company(), meta.companyFrom()), new Found(meta.title(), meta.titleFrom()),
				deadline.deadline(), deadline.from(), url, sections(split), keywords(split, body),
				new Source(PostingSections.cleanTitle(pageTitle), full));
	}

	/** 요약 페이지에서 쓰는 것. 문서는 여기서만 살아 있어 상세요강을 읽기 전에 버려진다. */
	private record MainPage(String text, Meta meta, Optional<String> iframe) {
	}

	private static MainPage readMain(Page page, FetchPlans.Plan plan, LocalDate today) {
		Document document = HtmlText.parse(page.html());
		return new MainPage(PostingSections.dropNoise(HtmlText.toText(document, false)), PostingMeta.read(document, today),
				plan.followIframes() ? FetchPlans.sameHostIframe(document, page.url()) : Optional.empty());
	}

	/** 헤더가 있는 섹션만, 항목이 있는 것만. 머리(헤더 없는 앞부분)는 원문 글자에만 있다. */
	private static List<SectionValue> sections(List<Section> sections) {
		return sections.stream()
			.filter((s) -> !PostingSections.HEAD.equals(s.name()))
			.map((s) -> new SectionValue(SECTION_TITLES.getOrDefault(s.name(), s.name()),
					s.lines().stream().map(PostingReader::item).filter((item) -> !item.isEmpty()).toList(), s.header()))
			.filter((s) -> !s.items().isEmpty())
			.toList();
	}

	/**
	 * 주요 업무·필수 요건·우대 사항에 나온 사전 낱말의 대표 표기. 셋 다 없거나 내용이 비었으면 섹션을 나눈 글 전체에서 찾는다.
	 * 많이 나온 순, 같으면 글자 순(jobfit 의 find)으로 MAX_KEYWORDS 개까지.
	 */
	private static List<String> keywords(List<Section> sections, String text) {
		// split 은 머리 줄만 있는 섹션도 남긴다("…경험\n우대 사항", 내용이 머리 줄에 붙은 "주요 업무 Java 로 API 개발")
		List<String> parts = sections.stream()
			.filter((s) -> KEYWORD_SECTIONS.contains(s.name()))
			.map(Section::text)
			.filter((t) -> !t.isEmpty())
			.toList();
		String scanned = parts.isEmpty() ? text : String.join("\n", parts);
		return KeywordDictionary.standard().find(scanned).keySet().stream().limit(MAX_KEYWORDS).toList();
	}

	private static String item(String line) {
		return Py.strip(ITEM_MARK.matcher(Py.strip(Py.strip(line), "| ")).replaceFirst(""));
	}

	/** 비어 있지 않은 첫 줄. 제목 표시(#)는 뗀다(jobfit 의 _first_line). */
	private static String firstLine(String text) {
		for (String line : Py.lines(text)) {
			String s = Py.strip(Py.lstrip(Py.strip(line), "# "));
			if (!s.isEmpty()) {
				return (Py.len(s) > FIRST_LINE_MAX) ? s.substring(0, s.offsetByCodePoints(0, FIRST_LINE_MAX)) : s;
			}
		}
		return "";
	}

}
