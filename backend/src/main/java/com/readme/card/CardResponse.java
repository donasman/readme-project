package com.readme.card;

import java.util.List;

/**
 * 카드 한 장. 목록(GET /api/cards)과 상세가 같은 모양이고, 프런트의 CardDetail 모양이다(경험카드 설계 4.1, 6절).
 *
 * @param summary         없으면 빈 글자
 * @param usableFor       문항 유형. 6종의 순서(지원동기, 직무역량, …)다
 * @param tags            대표 표기. 이름순이다(넣은 순서를 담는 칸이 없다)
 * @param facts           근거 숫자. 넣은 순서다
 * @param sourceId        뽑아 온 자료. 직접 만든 카드나 자료를 지운 카드는 null
 * @param sourceName      뽑아 온 파일의 이름. 자료가 없거나 붙여넣은 글이면 null
 * @param sourceExcerpt   카드가 나온 자료 조각(5,000자까지). 직접 만든 카드, 자료를 지운 카드, 추출 전에 만든 카드는 null
 * @param extractionNotes 추출 때 생긴 안내. 카드를 확인하면 빈다
 * @param issues          카드 검사 결과(CardRules)
 */
record CardResponse(long id, String title, String period, String org, String role, List<String> tags, String summary,
		List<String> usableFor, boolean verified, Star star, List<String> facts, String visibility, Long sourceId,
		String sourceName, String sourceExcerpt, List<String> extractionNotes, List<CardIssue> issues) {
}
