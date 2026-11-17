package com.readme.card;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.readme.card.CardRequests.CardInput;
import com.readme.extraction.ExtractedCard;
import com.readme.keyword.KeywordDictionary;
import com.readme.keyword.Numbers;
import com.readme.keyword.Numbers.NumToken;

/**
 * 카드 규칙: 문항 유형 읽기, 카드 검사, STAR 에만 있는 숫자. Spring 없이 돈다.
 *
 * <p>jobfit 의 src/jobfit/experiences.py(normalize_type, check_cards, loose_star_numbers, 기간 정규식)와
 * ai/exp_extract.py 의 ground_star(사람이 맞다고 한 카드의 길 vouched 와 자료 원문과 대조하는 길), normalize_cards 의 카드 한 장 부분을
 * 옮겼다.
 *
 * <p>check_cards 와 다른 점(경험카드 설계 4.1): 칸 이름과 글은 화면의 말("요약", "근거 숫자")로 쓴다. id·예시 카드·verified 검사는
 * 리드미에 해당하지 않아 뺐다(확인 여부는 화면이 따로 보여 준다). 사전에 없는 태그를 경고한다(jobfit 은 그대로 둔다).
 */
final class CardRules {

	/** 문항 유형 6종. card_usable_for 의 check 제약과 같은 값, 같은 순서다. */
	static final List<String> QUESTION_TYPES = List.of("지원동기", "직무역량", "협업", "문제해결", "성장", "기타");

	static final int SUMMARY_MAX = 60;

	private static final Map<String, String> TYPE_ALIASES = Map.of("기술역량", "직무역량", "역량", "직무역량", "전문성", "직무역량",
			"팀워크", "협업", "커뮤니케이션", "협업", "소통", "협업", "성장과정", "성장", "학습", "성장", "입사후포부", "지원동기", "포부", "지원동기");

	private static final List<String> STAR_LABELS = List.of("상황", "과제", "행동", "결과");

	private static final String PERIOD_PART = "\\d{4}(?:[-.]\\d{1,2})?";

	/** 기간: 2025-03 ~ 2025-06 · 2025.03 · 2026 ~ 현재 · 2024.09 – 2025.08. Python 처럼 \d·\s 는 모든 문자의 숫자·공백이다. */
	private static final Pattern PERIOD = Pattern.compile(
			"^" + PERIOD_PART + "(?:\\s*(?:~|–|—|\\s-\\s)\\s*(?:" + PERIOD_PART + "|현재)?)?$",
			Pattern.UNICODE_CHARACTER_CLASS);

	/** 자료 문장을 근거 숫자로 옮길 때 이보다 길면 숫자 앞뒤만 남긴다. */
	private static final int FACT_MAX = 120;

	private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?。])\\s+|\\n+", Pattern.UNICODE_CHARACTER_CLASS);

	private static final Pattern BULLET = Pattern.compile("^\\s*(?:[-*•·▪◦‣]|\\d{1,2}[.)])\\s*", Pattern.UNICODE_CHARACTER_CLASS);

	private static final String ERROR = "오류";

	private static final String WARNING = "경고";

	private CardRules() {
	}

	/** 검사에 쓰는 카드 칸. 태그와 문항 유형은 저장된 그대로다. */
	record Content(String title, String period, String summary, List<String> tags, List<String> usableFor, Star star,
			List<String> facts) {
	}

	/** 문항 유형 6종 중 하나로 읽는다. 공백을 지우고, 다른 이름(기술역량 → 직무역량)도 받는다. 아니면 null. */
	static String normalizeType(String value) {
		if (value == null) {
			return null;
		}
		String type = value.replaceAll("(?U)\\s+", "");
		return QUESTION_TYPES.contains(type) ? type : TYPE_ALIASES.get(type);
	}

	/** 카드 검사(jobfit 의 check_cards). 순서도 jobfit 과 같다: 빈 칸, 요약, 태그, 문항 유형, 근거 숫자, STAR, 기간. */
	static List<CardIssue> check(Content card) {
		List<CardIssue> issues = new ArrayList<>();
		requireFilled(issues, "제목", isMissing(card.title()));
		requireFilled(issues, "태그", card.tags().isEmpty());
		requireFilled(issues, "요약", isMissing(card.summary()));
		requireFilled(issues, "쓸 문항 유형", card.usableFor().isEmpty());
		String summary = (card.summary() != null) ? card.summary() : "";
		int length = summary.codePointCount(0, summary.length());
		if (length > SUMMARY_MAX) {
			issues.add(new CardIssue(WARNING, "요약", "요약이 %d자입니다(%d자 이내)".formatted(length, SUMMARY_MAX)));
		}
		KeywordDictionary dictionary = KeywordDictionary.standard();
		for (String tag : card.tags()) {
			String typed = tag.strip();
			String canonical = dictionary.normalize(typed);
			if (!typed.isEmpty() && !canonical.equals(typed)) {
				issues.add(new CardIssue(WARNING, "태그", "태그 '%s' 대신 대표 표기 '%s'(으)로 써 주세요".formatted(typed, canonical)));
			}
			else if (!typed.isEmpty() && !dictionary.contains(typed)) {
				issues.add(new CardIssue(WARNING, "태그", "태그 '%s'은(는) 키워드 사전에 없습니다".formatted(typed)));
			}
		}
		for (String type : card.usableFor()) {
			String normalized = normalizeType(type);
			if (normalized == null) {
				issues.add(new CardIssue(ERROR, "쓸 문항 유형",
						"쓸 문항 유형의 '%s'은(는) %s 중 하나여야 합니다".formatted(type, String.join(" · ", QUESTION_TYPES))));
			}
			else if (!normalized.equals(type.strip())) {
				issues.add(new CardIssue(WARNING, "쓸 문항 유형", "쓸 문항 유형의 '%s'은(는) '%s'(으)로 읽습니다".formatted(type, normalized)));
			}
		}
		if (card.facts().isEmpty()) {
			issues.add(new CardIssue(WARNING, "근거 숫자", "근거 숫자가 없습니다 — 이 카드로 쓴 초안에는 수치를 쓸 수 없습니다"));
		}
		List<String> empty = new ArrayList<>();
		List<String> parts = card.star().parts();
		for (int i = 0; i < STAR_LABELS.size(); i++) {
			if (parts.get(i).isBlank()) {
				empty.add(STAR_LABELS.get(i));
			}
		}
		if (!empty.isEmpty()) {
			issues.add(new CardIssue(WARNING, "STAR", "STAR에서 비어 있는 칸: " + String.join(", ", empty)));
		}
		List<String> loose = looseStarNumbers(card.star(), card.facts());
		if (!loose.isEmpty()) {
			issues.add(new CardIssue(WARNING, "STAR",
					"STAR에 근거 숫자에 없는 숫자가 있습니다: %s — 카드를 확인하면 근거 숫자에 넣습니다".formatted(String.join(", ", loose))));
		}
		String period = (card.period() != null) ? card.period().strip() : "";
		if (!period.isEmpty() && !PERIOD.matcher(period).find()) {
			issues.add(new CardIssue(WARNING, "기간", "기간 '%s'을(를) 읽을 수 없습니다 (예: 2025-03 ~ 2025-06)".formatted(period)));
		}
		return issues;
	}

	/** STAR 에만 있고 근거 숫자에 없는 숫자(글에 쓰인 그대로, 중복 없이). */
	static List<String> looseStarNumbers(Star star, List<String> facts) {
		List<NumToken> factNumbers = numbersIn(facts);
		LinkedHashSet<String> loose = new LinkedHashSet<>();
		for (String part : star.parts()) {
			for (NumToken number : Numbers.extract(part)) {
				if (!Numbers.sameValue(number, factNumbers)) {
					loose.add(number.raw());
				}
			}
		}
		return List.copyOf(loose);
	}

	/**
	 * 카드를 확인할 때 근거 숫자에 더할 문장들. STAR 에만 있는 숫자마다, 그 숫자가 든 STAR 문장(길면 숫자 앞뒤만)을 더한다.
	 * 사람이 카드를 보고 맞다고 했으니 자료 원문에 없어도 넣는다(jobfit 의 ground_star(vouched=True)).
	 *
	 * <p>shortcut: jobfit 은 자료 원문에 그 숫자가 있으면 STAR 문장 대신 원문 문장을 넣는다. 원문을 보는 길은 자료·추출
	 * (설계 4.2)과 함께 옮긴다. 그 전에는 자료에서 뽑은 카드도 STAR 문장을 넣는다.
	 */
	static List<String> factsToAdd(Star star, List<String> facts) {
		List<NumToken> factNumbers = numbersIn(facts);
		List<String> added = new ArrayList<>();
		for (String part : star.parts()) {
			for (NumToken number : Numbers.extract(part)) {
				if (Numbers.sameValue(number, factNumbers)) {
					continue;
				}
				String sentence = sentences(part).stream()
					.filter((s) -> Numbers.sameValue(number, Numbers.extract(s)))
					.findFirst()
					.orElse(part);
				String fact = clip(sentence, number.raw());
				List<NumToken> factNumbersOfFact = Numbers.extract(fact);
				if (!fact.isEmpty() && Numbers.sameValue(number, factNumbersOfFact)) {
					added.add(fact);
					factNumbers.addAll(factNumbersOfFact);
				}
			}
		}
		return added;
	}

	// ── 추출한 카드(jobfit ai/exp_extract.py 의 normalize_cards 중 카드 한 장, ground_star) ──────────────

	/** 원문 대조에 쓰는 자료 글. 숫자 뽑기는 글 길이에 따라 오래 걸려 조각마다 한 번만 한다. */
	record SourceText(String text, List<NumToken> numbers, List<String> sentences) {

		static SourceText of(String text) {
			return new SourceText(text, Numbers.extract(text), CardRules.sentences(text));
		}

	}

	/** 다듬은 추출 카드와 그때 생긴 안내(카드의 extractionNotes). */
	record Normalized(CardInput card, List<String> notes) {
	}

	/** STAR 를 맞춘 결과: 바뀐 STAR, 근거 숫자에 더할 문장, 안내. */
	record Grounding(Star star, List<String> factsToAdd, List<String> notes) {
	}

	/**
	 * LLM 이 뽑은 카드를 저장할 값으로 다듬는다. 제목이 비었으면 null.
	 * <ul>
	 * <li>태그는 대표 표기로(겹치면 하나), 문항 유형은 6종으로 읽고 아닌 것은 빼고 알린다.</li>
	 * <li>근거 숫자 가운데 자료 원문에 없는 숫자가 든 것은 빼고 알린다.</li>
	 * <li>STAR 에만 있는 숫자는 {@link #groundStar} 로 맞춘다.</li>
	 * <li>칸 길이를 넘는 글은 자른다(경험 이름·제목·기관·역할·요약 200, 기간 50, 이야기 한 칸 2,000, 태그 30개·100자, 근거 숫자 50개·500자).</li>
	 * </ul>
	 * jobfit 과 다른 점: 숫자를 그 카드가 나온 조각과 대조한다(jobfit 은 자료 전체). 숫자 뽑기가 글 길이에 따라 오래 걸려서다.
	 */
	static Normalized normalizeExtracted(ExtractedCard raw, SourceText source) {
		String title = cut(raw.title(), 200);
		if (title == null) {
			return null;
		}
		List<String> notes = new ArrayList<>();
		KeywordDictionary dictionary = KeywordDictionary.standard();
		LinkedHashSet<String> tags = new LinkedHashSet<>();
		for (String tag : strings(raw.tags())) {
			String canonical = cut(dictionary.normalize(tag), 100);
			if (canonical != null && tags.size() < 30) {
				tags.add(canonical);
			}
		}
		LinkedHashSet<String> types = new LinkedHashSet<>();
		List<String> badTypes = new ArrayList<>();
		for (String value : strings(raw.usableFor())) {
			String type = normalizeType(value);
			if (type != null) {
				types.add(type);
			}
			else {
				badTypes.add(value);
			}
		}
		if (!badTypes.isEmpty()) {
			notes.add("문항 유형이 아니라서 뺐습니다 — " + String.join(", ", badTypes));
		}
		ExtractedCard.StarText given = (raw.star() != null) ? raw.star()
				: new ExtractedCard.StarText("", "", "", "", "", "");
		Star star = new Star(orEmpty(cut(given.situation(), 2000)), orEmpty(cut(given.task(), 2000)),
				orEmpty(cut(given.action(), 2000)), orEmpty(cut(given.result(), 2000)), orEmpty(cut(given.obstacle(), 2000)),
				orEmpty(cut(given.learning(), 2000)));
		List<String> facts = new ArrayList<>();
		for (String fact : strings(raw.facts())) {
			List<NumToken> numbers = Numbers.extract(fact);
			if (!numbers.isEmpty() && !numbers.stream().allMatch((n) -> Numbers.sameValue(n, source.numbers()))) {
				notes.add("자료 원문에 없는 숫자가 있어 이 근거 숫자를 뺐습니다 — \"" + fact + "\"");
				continue;
			}
			facts.add(fact);
		}
		Grounding grounding = groundStar(star, facts, source);
		facts.addAll(grounding.factsToAdd());
		notes.addAll(grounding.notes());
		List<String> keptFacts = facts.stream().map((f) -> cut(f, 500)).filter((f) -> f != null).limit(50).toList();
		return new Normalized(new CardInput(cut(raw.experience(), 200), title, cut(raw.period(), 50), cut(raw.org(), 200),
				cut(raw.role(), 200), cut(raw.summary(), 200), grounding.star(), List.copyOf(tags), List.copyOf(types),
				keptFacts), notes);
	}

	/**
	 * STAR(어려움·배운 점 포함, 설계 4.8)에만 있고 근거 숫자에 없는 숫자를 맞춘다(jobfit 의 ground_star, 사람이 확인하기 전의 길). 초안은 근거 숫자의 숫자만 쓸 수 있다.
	 * <ul>
	 * <li>자료 원문에 있는 숫자 → 그 숫자가 든 원문 문장(STAR 문장과 가장 많이 겹치는 것, 길면 숫자 앞뒤만)을 근거 숫자에 더한다.</li>
	 * <li>원문에 없는 숫자 → STAR 에서 [확인 필요: 숫자] 로 바꾼다(초안이 그 숫자를 쓰지 않게).</li>
	 * </ul>
	 */
	static Grounding groundStar(Star star, List<String> facts, SourceText source) {
		List<NumToken> factNumbers = numbersIn(facts);
		List<String> added = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		List<String> parts = new ArrayList<>(star.parts());
		for (int i = 0; i < parts.size(); i++) {
			String value = parts.get(i);
			for (NumToken number : Numbers.extract(value)) {
				if (Numbers.sameValue(number, factNumbers)) {
					continue;
				}
				String fact = Numbers.sameValue(number, source.numbers()) ? sourceFact(number, source.sentences(), value)
						: null;
				if (fact != null) {
					added.add(fact);
					factNumbers.addAll(Numbers.extract(fact));
					notes.add("STAR에만 있던 숫자(" + number.raw() + ")를 근거 숫자에 넣었습니다 — \"" + fact + "\"");
				}
				else {
					String marked = markUnconfirmed(value, number.raw());
					if (!marked.equals(value)) {
						value = marked;
						notes.add("STAR의 숫자(" + number.raw() + ")는 자료 원문에 없어 [확인 필요: " + number.raw() + "]로 바꿨습니다");
					}
				}
			}
			parts.set(i, value);
		}
		return new Grounding(Star.of(parts), added, notes);
	}

	private static final Pattern CONFIRM_SPAN = Pattern.compile("\\[확인\\s*필요[^\\]]*\\]", Pattern.UNICODE_CHARACTER_CLASS);

	private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

	/** 숫자가 든 원문 문장 가운데 STAR 문장과 가장 많이 겹치는 것(같으면 앞의 것). 숫자가 표기 그대로 든 문장이 먼저다. */
	private static String sourceFact(NumToken number, List<String> sentences, String context) {
		Set<String> grams = bigrams(context);
		String best = null;
		int bestRaw = -1;
		int bestOverlap = -1;
		for (String sentence : sentences) {
			if (!Numbers.sameValue(number, Numbers.extract(sentence))) {
				continue;
			}
			int raw = Normalizer.normalize(sentence, Normalizer.Form.NFKC).contains(number.raw()) ? 1 : 0;
			Set<String> overlap = bigrams(sentence);
			overlap.retainAll(grams);
			if (raw > bestRaw || (raw == bestRaw && overlap.size() > bestOverlap)) {
				best = sentence;
				bestRaw = raw;
				bestOverlap = overlap.size();
			}
		}
		if (best == null) {
			return null;
		}
		String fact = clip(best, number.raw());
		return Numbers.sameValue(number, Numbers.extract(fact)) ? fact : null;
	}

	private static Set<String> bigrams(String text) {
		String s = SPACES.matcher(text).replaceAll("");
		Set<String> grams = new HashSet<>();
		for (int i = 0; i + 1 < s.length(); i++) {
			grams.add(s.substring(i, i + 2));
		}
		return grams;
	}

	/** value 의 raw(숫자 표기)를 [확인 필요: raw] 로. 이미 [확인 필요 …] 안에 있는 것과 더 긴 숫자의 일부는 건드리지 않는다. */
	static String markUnconfirmed(String value, String raw) {
		List<int[]> taken = new ArrayList<>();
		Matcher confirm = CONFIRM_SPAN.matcher(value);
		while (confirm.find()) {
			taken.add(new int[] { confirm.start(), confirm.end() });
		}
		Matcher matcher = Pattern.compile("(?<![\\d.,])" + Pattern.quote(raw) + "(?!\\d)", Pattern.UNICODE_CHARACTER_CLASS)
			.matcher(value);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (matcher.find()) {
			int start = matcher.start();
			if (taken.stream().anyMatch((span) -> span[0] <= start && start < span[1])) {
				continue;
			}
			out.append(value, last, start).append("[확인 필요: ").append(raw).append(']');
			last = matcher.end();
		}
		return (last == 0) ? value : out.append(value.substring(last)).toString();
	}

	private static List<String> strings(List<String> values) {
		return (values == null) ? List.of()
				: values.stream().filter((v) -> v != null && !v.isBlank()).map(String::strip).toList();
	}

	private static String orEmpty(String text) {
		return (text != null) ? text : "";
	}

	/** 앞뒤 공백을 지우고 max 자까지. 비면 null. */
	private static String cut(String text, int max) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String s = text.strip();
		return (s.length() <= max) ? s : s.substring(0, max).strip();
	}

	private static void requireFilled(List<CardIssue> issues, String field, boolean empty) {
		if (empty) {
			issues.add(new CardIssue(ERROR, field, field + " 칸이 비어 있습니다"));
		}
	}

	private static boolean isMissing(String text) {
		return text == null || text.isEmpty();
	}

	private static List<NumToken> numbersIn(List<String> texts) {
		List<NumToken> numbers = new ArrayList<>();
		texts.forEach((text) -> numbers.addAll(Numbers.extract(text)));
		return numbers;
	}

	private static List<String> sentences(String text) {
		return Arrays.stream(SENTENCE_SPLIT.split(text, -1))
			.map((part) -> BULLET.matcher(part).replaceAll("").strip())
			.filter((sentence) -> !sentence.isEmpty())
			.toList();
	}

	/**
	 * 긴 문장은 숫자 앞뒤만: 낱말 경계에서 자르고 잘린 쪽에 … 를 붙인다(jobfit 의 _clip).
	 *
	 * <p>shortcut: 길이를 UTF-16 글자로 센다(jobfit 은 코드 포인트). 120자가 넘는 문장에 이모지가 들 때만 자르는 자리가 다르다.
	 */
	private static String clip(String sentence, String raw) {
		String s = sentence.strip();
		if (s.length() <= FACT_MAX) {
			return s;
		}
		int pos = s.indexOf(raw);
		if (pos < 0) {
			s = Normalizer.normalize(s, Normalizer.Form.NFKC);
			pos = Math.max(s.indexOf(raw), 0);
		}
		int half = (FACT_MAX - raw.length()) / 2;
		int start = Math.max(0, pos - half);
		int end = Math.min(s.length(), pos + raw.length() + half);
		if (start > 0) { // 낱말 중간에서 자르지 않게 안쪽 띄어쓰기까지. 숫자는 넘지 않는다
			int space = s.indexOf(' ', start);
			start = (space >= 0 && space < pos) ? space + 1 : start;
		}
		if (end < s.length()) {
			int space = s.lastIndexOf(' ', end - 1);
			end = (space >= pos + raw.length()) ? space : end;
		}
		// 숫자 표기가 문장보다 길면(숫자와 단위 사이에 줄이 많은 것) start 가 end 를 넘는다. Python 의 s[start:end] 처럼 빈 글자로
		start = Math.min(start, end);
		return (start > 0 ? "…" : "") + s.substring(start, end).strip() + (end < s.length() ? "…" : "");
	}

}
