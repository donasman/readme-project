package com.readme.extraction;

import java.util.List;

/**
 * 추출 작업이 자료 조각 하나에서 카드를 뽑았다. card 패키지(카드 테이블의 주인)가 같은 트랜잭션에서 받아 저장한다.
 * card 가 extraction 을 부르는 쪽이라(이 이벤트와 원문 조각), extraction 이 card 를 부르지 않도록 이벤트로 넘긴다.
 *
 * @param part       조각 번호(1부터)
 * @param sourceText 그 조각의 글(가린 본문의 조각). 카드의 숫자를 이 글과 대조한다
 * @param cards      이미 있는 카드와 제목이 같은 것은 뺀 카드들
 */
public record CardsExtracted(long userId, long sourceId, long jobId, int part, String sourceText,
		List<ExtractedCard> cards) {
}
