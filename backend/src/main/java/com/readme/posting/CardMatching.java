package com.readme.posting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.readme.keyword.KeywordDictionary;

/**
 * 공고와 내 카드 맞추기(화면·API 대응 3절 13번, 공고 설계 4.8). jobfit 의 match.py 중 build_match 와 terms_of 를 옮겼다. Spring 없이
 * 돈다.
 *
 * <ul>
 * <li>공고 분석의 필수·우대 요건마다 글에서 낱말(사전의 대표 표기, 또는 글에 그대로 있는 카드 태그)을 찾고, 그 낱말이 태그에 있는 카드가
 * 그 요건을 맞춘다. 카드 태그에는 포함 관계를 더한다(Spring Boot → Spring → Java).</li>
 * <li>카드 순위: 맞춘 필수 요건 3점, 우대 요건 2점, 공고 키워드 1점. 점수는 보이지 않는다(화면·API 대응 3절 13번).</li>
 * <li>직무 이름(백엔드 등)은 거의 모든 공고·카드에 있어 낱말로 치지 않는다.</li>
 * <li>요건마다 결과를 낸다(공고 설계 4.8, 화면·API 대응 3.3의 26번): 카드가 맞추면 matched, 낱말이 있는데 맞춘 카드가 없으면
 * unmatched, 낱말이 없어 카드로 판단할 수 없는데 프로필의 자격증·어학·전공이 글에 있으면 profile, 그 밖에는 unknown. 낱말이 있는 요건은
 * 프로필이 가리지 않게 카드로만 정하고, 글에 있는 프로필 값은 profileItems 로 함께 싣는다("정보처리기사 및 Kubernetes 운영"). 프로필은
 * 넣지 않아도 되는 값이라 있을 때만 맞춘 것으로 보고, 없다고 unmatched 로 바꾸지 않는다.</li>
 * </ul>
 * 옮기지 않은 것: 전체 점수(_score, 보이지 않는다), 문항별 카드 배정(assign, 쓰는 화면이 없다. 자소서 초안 때 옮긴다).
 */
final class CardMatching {

	/** jobfit 의 ROLE_WORDS */
	private static final Set<String> ROLE_WORDS = Set.of("백엔드", "프런트엔드", "풀스택", "서버 개발", "데이터 엔지니어");

	private CardMatching() {
	}

	/** 맞출 카드. experience 는 경험 이름(없으면 빈 글자), tags 는 저장된 태그(대표 표기)다. */
	record MatchCard(long id, String title, String experience, List<String> tags) {
	}

	/**
	 * 맞는 카드 하나. 프런트의 CardMatch 모양이다.
	 *
	 * @param experience 경험 이름. 없으면 빈 글자(카드 응답과 같다)
	 * @param overlap    이 카드가 맞춘 요건의 낱말과 공고 키워드 가운데 카드 태그에 있는 것(처음 나온 순)
	 */
	record CardMatch(long cardId, String title, String experience, List<String> overlap) {
	}

	/**
	 * 한 요건을 맞춘 카드 하나.
	 *
	 * @param overlap 이 요건의 낱말 가운데 그 카드의 태그(포함 관계를 더한 것)에 있는 것. 요건 글에 나온 순이다
	 */
	record RequirementCard(long cardId, List<String> overlap) {
	}

	/**
	 * 요건과 맞춰 볼 내 프로필 값: 자격증 이름(대표 표기), 어학 시험 이름, 전공.
	 */
	record ProfileFacts(List<String> certificates, List<String> languages, List<String> majors) {

		static final ProfileFacts EMPTY = new ProfileFacts(List.of(), List.of(), List.of());

	}

	/**
	 * 필수·우대 요건 하나의 결과. 프런트의 RequirementResult 모양이다.
	 *
	 * @param kind         must(필수) 또는 nice(우대)
	 * @param status       matched, profile, unmatched, unknown(클래스 설명)
	 * @param cards        맞춘 내 카드와 겹친 낱말. 먼저 만든 카드가 앞이다. 모두 matches 에도 있다. matched 일 때만 있다
	 * @param profileItems 요건 글에 있는 내 프로필 값(예: 정보처리기사). profile 이나 unmatched 일 때 있을 수 있다
	 */
	record RequirementResult(String kind, String text, String status, List<RequirementCard> cards,
			List<String> profileItems) {
	}

	/**
	 * @param matches      한 요건이나 키워드라도 맞춘 카드. 점수가 높은 것이 앞, 같으면 받은 순서다
	 * @param unmatched    카드로 답하지 못한 요건: 결과가 unmatched 인 필수·우대 요건. 필수가 앞이다
	 * @param requirements 필수·우대 요건마다의 결과. 필수가 앞, 분석의 순서다
	 */
	record Result(List<CardMatch> matches, List<String> unmatched, List<RequirementResult> requirements) {

		static final Result EMPTY = new Result(List.of(), List.of(), List.of());

	}

	/** 한 요건: 낱말과, 그 낱말을 태그로 가진 카드들. */
	record Requirement(String kind, String text, List<String> terms, List<Long> cards) {
	}

	static Result match(PostingAnalysis analysis, List<MatchCard> cards, ProfileFacts profile,
			KeywordDictionary dictionary) {
		if (analysis == null) {
			return Result.EMPTY;
		}
		Map<Long, List<String>> expanded = new LinkedHashMap<>();
		Map<Long, Set<String>> lowered = new LinkedHashMap<>();
		LinkedHashSet<String> allTags = new LinkedHashSet<>();
		for (MatchCard card : cards) {
			List<String> tags = dictionary
				.expand(card.tags().stream().map(dictionary::normalize).distinct().toList());
			expanded.put(card.id(), tags);
			lowered.put(card.id(), new HashSet<>(tags.stream().map(CardMatching::lower).toList()));
			allTags.addAll(tags);
		}
		List<Requirement> requirements = requirements(analysis, lowered, List.copyOf(allTags), dictionary);
		List<String> keywords = orEmpty(analysis.keywords()).stream().filter((k) -> !ROLE_WORDS.contains(k)).toList();

		List<CardMatch> matches = new ArrayList<>();
		List<Integer> scores = new ArrayList<>();
		for (MatchCard card : cards) {
			Set<String> tags = lowered.get(card.id());
			LinkedHashMap<String, String> overlap = new LinkedHashMap<>();
			int score = 0;
			for (Requirement requirement : requirements) {
				if (requirement.cards().contains(card.id())) {
					score += requirement.kind().equals("must") ? 3 : 2;
					requirement.terms().stream().filter((t) -> tags.contains(lower(t))).forEach((t) -> overlap.putIfAbsent(lower(t), t));
				}
			}
			for (String keyword : keywords) {
				if (tags.contains(lower(keyword))) {
					score += 1;
					overlap.putIfAbsent(lower(keyword), keyword);
				}
			}
			if (score > 0) {
				matches.add(new CardMatch(card.id(), card.title(), card.experience(), List.copyOf(overlap.values())));
				scores.add(score);
			}
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < matches.size(); i++) {
			order.add(i);
		}
		order.sort(Comparator.comparingInt((Integer i) -> -scores.get(i))); // 같은 점수는 받은 순서(안정 정렬)
		List<RequirementResult> results = requirements.stream().map((r) -> result(r, lowered, profile)).toList();
		List<String> unmatched = results.stream()
			.filter((r) -> r.status().equals("unmatched"))
			.map(RequirementResult::text)
			.toList();
		return new Result(order.stream().map(matches::get).toList(), unmatched, results);
	}

	private static RequirementResult result(Requirement requirement, Map<Long, Set<String>> lowered,
			ProfileFacts profile) {
		if (!requirement.cards().isEmpty()) {
			List<RequirementCard> cards = requirement.cards()
				.stream()
				.map((id) -> new RequirementCard(id,
						requirement.terms().stream().filter((t) -> lowered.get(id).contains(lower(t))).toList()))
				.toList();
			return new RequirementResult(requirement.kind(), requirement.text(), "matched", cards, List.of());
		}
		List<String> items = profileItems(requirement.text(), profile);
		String status = !requirement.terms().isEmpty() ? "unmatched" : items.isEmpty() ? "unknown" : "profile";
		return new RequirementResult(requirement.kind(), requirement.text(), status, List.of(), items);
	}

	/**
	 * 요건 글에 있는 내 프로필 값. 자격증은 자격증 사전으로 다른 표기(정처기)도 찾고, 사전 밖의 이름과 어학 시험, 전공은 글에 그대로 있을
	 * 때만 본다(키워드와 같은 경계 규칙). 한 글자 값은 뺀다.
	 */
	static List<String> profileItems(String text, ProfileFacts profile) {
		KeywordDictionary certificates = KeywordDictionary.certificates();
		Set<String> named = new HashSet<>(certificates.find(text).keySet().stream().map(CardMatching::lower).toList());
		LinkedHashSet<String> items = new LinkedHashSet<>();
		for (String name : profile.certificates()) {
			if (named.contains(lower(certificates.normalize(name))) || mentions(text, name)) {
				items.add(name);
			}
		}
		for (String value : profile.languages()) {
			if (mentions(text, value)) {
				items.add(value);
			}
		}
		for (String value : profile.majors()) {
			if (mentions(text, value)) {
				items.add(value);
			}
		}
		return List.copyOf(items);
	}

	private static boolean mentions(String text, String value) {
		return value.strip().length() >= 2 && KeywordDictionary.mentions(text, value.strip());
	}

	static List<Requirement> requirements(PostingAnalysis analysis, Map<Long, Set<String>> lowered, List<String> allTags,
			KeywordDictionary dictionary) {
		List<Requirement> requirements = new ArrayList<>();
		for (String kind : List.of("must", "nice")) {
			for (String text : orEmpty(kind.equals("must") ? analysis.must() : analysis.nice())) {
				List<String> terms = termsOf(text, dictionary, allTags);
				Set<String> low = new HashSet<>(terms.stream().map(CardMatching::lower).toList());
				List<Long> covered = lowered.entrySet()
					.stream()
					.filter((e) -> e.getValue().stream().anyMatch(low::contains))
					.map(Map.Entry::getKey)
					.toList();
				requirements.add(new Requirement(kind, text, terms, covered));
			}
		}
		return requirements;
	}

	/**
	 * 글의 낱말(jobfit 의 terms_of): 사전의 대표 표기, 그리고 사전에 없는 카드 태그(예: '배치')도 글에 그대로 있으면 낱말로 친다.
	 * 사전 낱말은 경계를 지켜 이미 찾았으므로 다시 찾지 않는다. 직무 이름과 한 글자 태그는 뺀다. 글에 처음 나온 순이다(공고 설계 4.8의
	 * 요건마다 겹친 낱말). jobfit 은 사전 낱말을 많이 나온 순으로 둔다.
	 */
	static List<String> termsOf(String text, KeywordDictionary dictionary, List<String> extraTags) {
		Map<String, Integer> found = new HashMap<>();
		dictionary.firstPositions(text).forEach((term, at) -> {
			if (!ROLE_WORDS.contains(term)) {
				found.put(term, at);
			}
		});
		Set<String> seen = new HashSet<>(found.keySet().stream().map(CardMatching::lower).toList());
		for (String tag : extraTags) {
			if (tag.length() < 2 || dictionary.contains(tag) || ROLE_WORDS.contains(tag) || seen.contains(lower(tag))) {
				continue;
			}
			int at = KeywordDictionary.indexOf(text, tag);
			if (at >= 0) {
				found.put(tag, at);
				seen.add(lower(tag));
			}
		}
		// 같은 자리면 글자 순(사전 낱말끼리는 가린 자리라 겹치지 않는다)
		return found.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
			.map(Map.Entry::getKey)
			.toList();
	}

	private static String lower(String text) {
		return text.toLowerCase(Locale.ROOT);
	}

	private static <T> List<T> orEmpty(List<T> values) {
		return (values != null) ? values : List.of();
	}

}
