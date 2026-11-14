package com.readme.postingread;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.readme.common.error.ApiException;
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

	private final PageFetcher fetcher;

	PostingReader(PageFetcher fetcher) {
		this.fetcher = fetcher;
	}

	/**
	 * 붙여넣은 본문. 글은 고치지 않는다(jobfit 의 _clean_pasted). 본문이 없어 보여도 막지 않는다. 사용자가 본문이라고 준 것이다.
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
		return PostingReadResponse.read(company, title, deadline.deadline(), deadline.from(),
				(sourceUrl != null) ? sourceUrl.strip() : "", sections(PostingSections.split(clean)), new Source(first, clean));
	}

	/**
	 * 링크. 사이트 규칙대로 요약 페이지를 받고, 거기에 공고 본문이 없으면 같은 공고의 상세요강(또는 같은 호스트 iframe 하나)을 받는다.
	 * 둘 다 본문이 없으면 422 FETCH_FAILED 다(로그인 필요, 스크립트로 그리는 화면, 그림 공고).
	 */
	PostingReadResponse fromUrl(String url, LocalDate today) {
		FetchPlans.Plan plan = FetchPlans.plan(url);
		Page main = this.fetcher.html(plan.mainUrl(), plan.referer());
		String mainText = PostingSections.dropNoise(HtmlText.toText(main.html()));
		String detailText = "";
		if (!PostingSections.hasPostingBody(mainText)) {
			Optional<String> detail = plan.detailUrls().stream().findFirst();
			if (detail.isEmpty() && plan.followIframes()) {
				detail = FetchPlans.sameHostIframe(main.html(), main.url());
			}
			if (detail.isPresent()) {
				detailText = PostingSections.dropNoise(HtmlText.toText(this.fetcher.html(detail.get(), plan.mainUrl()).html()));
			}
		}
		String body = (!detailText.isEmpty() && PostingSections.hasPostingBody(detailText)) ? detailText : mainText;
		if (!PostingSections.hasPostingBody(body)) {
			throw new ApiException(PostingReadErrorCode.FETCH_FAILED,
					"공고 본문을 찾지 못했습니다. 로그인이 필요하거나, 화면을 스크립트로 그리거나, 그림으로 된 공고일 수 있습니다. 공고 본문을 복사해 붙여넣어 주세요");
		}
		Meta meta = PostingMeta.read(main.html(), today);
		Dated deadline = meta.deadline().known() ? new Dated(meta.deadline(), meta.deadlineFrom())
				: PostingMeta.deadlineFromSummary(detailText, today);
		String full = Stream.of(mainText, detailText).filter((t) -> !t.isEmpty()).collect(Collectors.joining("\n\n"));
		String pageTitle = !meta.pageTitle().isEmpty() ? meta.pageTitle() : firstLine(detailText.isEmpty() ? mainText : detailText);
		return PostingReadResponse.read(new Found(meta.company(), meta.companyFrom()), new Found(meta.title(), meta.titleFrom()),
				deadline.deadline(), deadline.from(), url, sections(PostingSections.split(body)),
				new Source(PostingSections.cleanTitle(pageTitle), full));
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
