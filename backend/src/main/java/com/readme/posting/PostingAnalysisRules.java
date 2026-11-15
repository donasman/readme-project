package com.readme.posting;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.readme.keyword.KeywordDictionary;
import com.readme.posting.PostingAnalysis.Question;

/**
 * 공고 분석(공고 설계 4.7)에 보낼 글과 받은 답의 검사. Spring 없이 도는 규칙이다.
 *
 * <p>jobfit 에서 옮겼다(구조 문서 10절): clean/posting.py 의 프롬프트용 압축본(섹션 우선순위 _order, _compose_posting,
 * _truncate, 연락처 가리기 mask_contacts)과 ai/analyze.py 의 답 검사(validate, verify_question). 한 줄 요약(role_summary)과
 * 인재상(values)은 쓰는 화면이 없어 옮기지 않았다. 재요청 프롬프트와 캐시는 파일 상태라 옮기지 않았다(분석은 공고 본체에 한 번 둔다).
 *
 * <p>글자 수는 Python 의 len 처럼 코드 포인트로 센다. 공백은 Python re 의 \s 처럼 유니코드 공백에 \x1c~\x1f 를 더한 것이다.
 */
final class PostingAnalysisRules {

	/** 프롬프트에 넣는 공고 글의 길이(코드 포인트). jobfit 의 POSTING_LIMIT. 넘으면 우선순위가 낮은 묶음부터 뺀다. */
	static final int POSTING_LIMIT = 2_500;

	/** 프롬프트에 참고로 넣는, 사전이 글에서 찾은 키워드 수. jobfit 의 _KEYWORDS_IN_PROMPT. */
	static final int KEYWORDS_IN_PROMPT = 20;

	/** 목록마다 남기는 수. jobfit 의 LIST_LIMITS(인재상 values 는 옮기지 않았다). */
	static final int MUST = 6;

	static final int NICE = 6;

	static final int DUTIES = 5;

	static final int KEYWORDS = 15;

	/**
	 * 프롬프트에 넣는 순서. jobfit 의 SECTION_PRIORITY 를 화면의 묶음 이름(읽어 오기가 쓰는 이름)으로 옮겼다.
	 * "" 은 jobfit 의 머리(헤더 없는 본문) 자리다. 리드미는 머리를 저장하지 않으므로 이름을 모르는 묶음(손으로 넣은 이름)을 여기에 둔다.
	 */
	private static final List<String> PRIORITY = List.of("필수 요건", "주요 업무", "우대 사항", "자기소개서", "인재상", "모집 부문",
			"전형 절차", "근무 조건", "접수 방법", "회사 소개", "", "복리후생");

	private static final String CUT_MARK = " …(잘림)";

	/** Python re 의 \s(유니코드). Java 의 유니코드 \s 에는 \x1c~\x1f 가 없다. */
	private static final String SPACE = "[\\s\\x1c-\\x1f]";

	private static final int U = Pattern.UNICODE_CHARACTER_CLASS;

	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	/** 맨 앞의 0 은 ASCII 0 이다. 뒤의 숫자와 공백은 Python 처럼 유니코드다. */
	private static final Pattern PHONE = Pattern
		.compile("(?<!\\d)0\\d{1,2}[-.\\s\\x1c-\\x1f]?\\d{3,4}[-.\\s\\x1c-\\x1f]?\\d{4}(?!\\d)", U);

	/** 문항 앞의 번호와 글머리. jobfit 의 _LEADING_MARK. */
	private static final Pattern LEADING_MARK = Pattern.compile("^" + SPACE + "*(?:(?:Q|문항)?" + SPACE + "*\\d{1,2}" + SPACE
			+ "*[.)．:]|\\(\\d{1,2}\\)|[-•·ㆍ▪*■□●○※])" + SPACE + "*", U);

	private static final Pattern QUOTES = Pattern.compile("[\"'“”‘’「」『』]");

	private static final Pattern SPACES = Pattern.compile(SPACE + "+", U);

	/** 원문 대조에서 문항으로 볼 가장 짧은 길이(공백·따옴표를 뺀 코드 포인트). 이보다 짧으면 판정하지 않는다. */
	private static final int QUESTION_MIN = 6;

	/** 글자 수 숫자를 찾는 범위: 문항 앞 30자, 뒤 60자(공백을 뺀 글). */
	private static final int BEFORE = 30;

	private static final int AFTER = 60;

	private PostingAnalysisRules() {
	}

	/**
	 * LLM 의 답. 이 record 의 JSON 스키마로 구조화 출력을 받는다. 프롬프트(prompts/posting-analysis.md)가 칸을 설명한다.
	 * 받은 값은 {@link #check} 로 거른다.
	 */
	record Answer(List<String> must, List<String> nice, List<String> duties, List<String> keywords,
			List<Question> questions) {

		/** @param limit 글자 수. 공고에 숫자로 없으면 0 */
		record Question(String text, int limit) {
		}

	}

	/**
	 * LLM 에 보낼 것.
	 *
	 * @param text 사용자 글: 사전이 찾은 키워드(참고), 회사·공고명, 우선순위로 줄이고 연락처를 가린 공고 글
	 * @param full 묶음 전체의 글. 줄이지도 가리지도 않는다. 문항을 원문과 대조하는 데 쓴다(jobfit 의 posting.full.txt)
	 */
	record Input(String text, String full) {
	}

	/**
	 * 답을 검사한 결과.
	 *
	 * @param analysis 저장할 분석. 쓸 수 없는 답이면 null
	 * @param errors   쓸 수 없는 이유. 공고 글은 넣지 않는다(기록에 남긴다)
	 * @param dropped  공고 글에 없어 버린 문항
	 */
	record Checked(PostingAnalysis analysis, List<String> errors, List<String> dropped) {
	}

	/**
	 * 문항 원문 대조의 결과.
	 *
	 * @param inPosting   공고 글에 있다
	 * @param limitNearby 글자 수 숫자가 그 문항 가까이에 있다. 글자 수가 없으면 늘 true
	 */
	record Found(boolean inPosting, boolean limitNearby) {
	}

	// ── 보낼 글 ─────────────────────────────────────────────────────────

	static Input input(String company, String title, List<PostSection> sections, KeywordDictionary dictionary) {
		String full = compose(sections.stream().map(Part::of).toList());
		List<String> found = dictionary.find(full).keySet().stream().limit(KEYWORDS_IN_PROMPT).toList();
		String text = "## 글에서 찾은 키워드 (참고)\n" + (found.isEmpty() ? "(없음)" : String.join(", ", found)) + "\n\n## 공고 — "
				+ company + " / " + title + "\n" + maskContacts(posting(sections));
		return new Input(text, full);
	}

	/**
	 * 공고 글. 묶음을 우선순위대로 놓고, 한도를 넘으면 우선순위가 낮은 묶음부터 통째로 빼고, 하나만 남아도 넘치면 그 묶음을 자른다
	 * (jobfit 의 _truncate).
	 */
	static String posting(List<PostSection> sections) {
		List<Part> kept = new ArrayList<>(ordered(sections).stream().map(Part::of).toList());
		while (!kept.isEmpty() && length(compose(kept)) > POSTING_LIMIT) {
			if (kept.size() == 1) {
				Part last = kept.getFirst();
				// 머리 줄 + 본문 + " …(잘림)" 이 한도 안에 들도록 여유를 둔다(jobfit 그대로)
				int budget = POSTING_LIMIT - length(last.title()) - 20;
				return compose(List.of(new Part(last.title(), take(last.body(), Math.max(budget, 200)) + CUT_MARK)));
			}
			kept.removeLast();
		}
		return compose(kept);
	}

	/** 우선순위대로. 필수 요건도 주요 업무도 없으면 이름 모를 묶음(jobfit 의 머리)이 그 역할이라 맨 앞이다(jobfit 의 _order). */
	private static List<PostSection> ordered(List<PostSection> sections) {
		boolean unknownFirst = sections.stream().noneMatch((s) -> s.title().equals("필수 요건") || s.title().equals("주요 업무"));
		return sections.stream().sorted(Comparator.comparingInt((PostSection s) -> rank(s.title(), unknownFirst))).toList();
	}

	private static int rank(String title, boolean unknownFirst) {
		int index = PRIORITY.indexOf(title);
		if (index >= 0 && !title.isEmpty()) {
			return index;
		}
		return unknownFirst ? -1 : PRIORITY.indexOf("");
	}

	private static String compose(List<Part> parts) {
		return parts.stream().map((part) -> "## " + part.title() + "\n" + part.body()).collect(Collectors.joining("\n\n"));
	}

	/** 묶음 하나의 글: 머리는 이름, 본문은 항목마다 "- " 줄이다. */
	private record Part(String title, String body) {

		static Part of(PostSection section) {
			return new Part(section.title(),
					section.items().stream().map((item) -> "- " + item).collect(Collectors.joining("\n")));
		}

	}

	/** 담당자 이메일과 전화를 가린다. 이메일을 먼저 가린다(jobfit 의 mask_contacts). */
	static String maskContacts(String text) {
		return PHONE.matcher(EMAIL.matcher(text).replaceAll("[이메일]")).replaceAll("[전화]");
	}

	// ── 답 검사 ─────────────────────────────────────────────────────────

	/**
	 * 답을 거른다(jobfit 의 validate). 목록마다 앞뒤 공백을 지우고, 빈 항목과 겹치는 항목을 빼고, 개수를 넘으면 앞에서 자른다.
	 * 키워드는 사전의 대표 표기로 바꾼다(사전에 없으면 그대로). 필수 역량과 주요 업무가 둘 다 비면 쓸 수 없는 답이다.
	 * 문항은 공고 글에 있는 것만 두고, 글자 수는 그 문항 가까이에 그 숫자가 있을 때만 둔다.
	 */
	static Checked check(Answer answer, String full, KeywordDictionary dictionary) {
		List<String> must = items(answer.must(), MUST, UnaryOperator.identity());
		List<String> duties = items(answer.duties(), DUTIES, UnaryOperator.identity());
		List<String> nice = items(answer.nice(), NICE, UnaryOperator.identity());
		List<String> keywords = items(answer.keywords(), KEYWORDS, dictionary::normalize);
		List<Question> kept = new ArrayList<>();
		List<String> dropped = new ArrayList<>();
		for (Answer.Question question : orEmpty(answer.questions())) {
			if (question == null || question.text() == null || question.text().isBlank()) {
				continue;
			}
			String text = question.text().strip();
			Integer limit = (question.limit() > 0) ? question.limit() : null;
			Found found = verifyQuestion(text, limit, full);
			if (!found.inPosting()) {
				dropped.add(text);
				continue;
			}
			kept.add(new Question(text, found.limitNearby() ? limit : null));
		}
		if (must.isEmpty() && duties.isEmpty()) {
			return new Checked(null, List.of("must(필수 역량)와 duties(주요 업무)가 둘 다 비어 있습니다"), dropped);
		}
		return new Checked(new PostingAnalysis(must, nice, duties, keywords, kept), List.of(), dropped);
	}

	private static List<String> items(List<String> values, int limit, UnaryOperator<String> normalize) {
		LinkedHashSet<String> out = new LinkedHashSet<>();
		orEmpty(values).stream()
			.filter(Objects::nonNull)
			.map(String::strip)
			.filter((value) -> !value.isEmpty())
			.map(normalize)
			.forEach(out::add);
		return out.stream().limit(limit).toList();
	}

	/**
	 * 문항이 공고 글에 있는가, 글자 수 숫자가 그 문항 가까이(앞 30자, 뒤 60자)에 있는가(jobfit 의 verify_question).
	 * 공백·따옴표와 문항 앞의 번호 하나는 무시하고 NFKC 로 맞춰 견준다. 6자보다 짧으면 판정하지 않는다(없는 것으로 본다).
	 */
	static Found verifyQuestion(String text, Integer limit, String full) {
		String question = norm(LEADING_MARK.matcher(text).replaceFirst(""));
		String posting = norm(full);
		if (length(question) < QUESTION_MIN) {
			return new Found(false, false);
		}
		int at = posting.indexOf(question);
		if (at < 0) {
			return new Found(false, false);
		}
		if (limit == null) {
			return new Found(true, true);
		}
		String window = posting.substring(move(posting, at, -BEFORE), move(posting, at + question.length(), AFTER));
		return new Found(true,
				window.contains(String.valueOf(limit)) || window.contains(String.format(Locale.ROOT, "%,d", limit)));
	}

	private static String norm(String text) {
		return SPACES.matcher(QUOTES.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll(""))
			.replaceAll("");
	}

	/** index 에서 코드 포인트로 by 만큼 옮긴 자리. 글의 처음과 끝을 넘지 않는다(Python 의 잘라내기처럼). */
	private static int move(String text, int index, int by) {
		int available = (by < 0) ? text.codePointCount(0, index) : text.codePointCount(index, text.length());
		return text.offsetByCodePoints(index, Integer.signum(by) * Math.min(Math.abs(by), available));
	}

	private static String take(String text, int codePoints) {
		return text.substring(0, move(text, 0, codePoints));
	}

	private static int length(String text) {
		return text.codePointCount(0, text.length());
	}

	private static <T> List<T> orEmpty(List<T> values) {
		return (values != null) ? values : List.of();
	}

}
