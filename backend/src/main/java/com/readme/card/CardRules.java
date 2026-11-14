package com.readme.card;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.readme.keyword.KeywordDictionary;
import com.readme.keyword.Numbers;
import com.readme.keyword.Numbers.NumToken;

/**
 * 카드 규칙: 문항 유형 읽기, 카드 검사, STAR 에만 있는 숫자. Spring 없이 돈다.
 *
 * <p>jobfit 의 src/jobfit/experiences.py(normalize_type, check_cards, loose_star_numbers, 기간 정규식)와
 * ai/exp_extract.py 의 ground_star 중 "자료 원문 없이 사람이 맞다고 한 카드"의 길(vouched)을 옮겼다.
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
		for (int i = 0; i < parts.size(); i++) {
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
			for (NumToken number : Numbers.extract(part).numbers()) {
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
			for (NumToken number : Numbers.extract(part).numbers()) {
				if (Numbers.sameValue(number, factNumbers)) {
					continue;
				}
				String sentence = sentences(part).stream()
					.filter((s) -> Numbers.sameValue(number, Numbers.extract(s).numbers()))
					.findFirst()
					.orElse(part);
				String fact = clip(sentence, number.raw());
				List<NumToken> factNumbersOfFact = Numbers.extract(fact).numbers();
				if (!fact.isEmpty() && Numbers.sameValue(number, factNumbersOfFact)) {
					added.add(fact);
					factNumbers.addAll(factNumbersOfFact);
				}
			}
		}
		return added;
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
		texts.forEach((text) -> numbers.addAll(Numbers.extract(text).numbers()));
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
