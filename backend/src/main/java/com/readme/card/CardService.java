package com.readme.card;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.card.CardRequests.CardInput;
import com.readme.common.error.ApiException;

/**
 * 카드를 바꾸는 일. 남의 카드는 없는 카드와 같은 404 CARD_NOT_FOUND 다.
 * 응답에 실을 카드는 이 클래스의 트랜잭션이 끝난 뒤 컨트롤러가 CardQueries 로 읽는다.
 */
@Service
class CardService {

	private final ExperienceCardRepository cards;

	private final Clock clock;

	CardService(ExperienceCardRepository cards, Clock clock) {
		this.cards = cards;
		this.clock = clock;
	}

	/** 카드를 직접 만든다. 확인 전(verified = false)으로 들어간다. 새 카드의 id 를 돌려준다. */
	@Transactional
	long create(long userId, CardInput card) {
		return this.cards.save(new ExperienceCard(userId, card, this.clock.instant())).getId();
	}

	/** 모든 칸을 바꾼다. 확인 여부는 그대로다. */
	@Transactional
	void update(long userId, long cardId, CardInput card) {
		mine(userId, cardId).edit(card, this.clock.instant());
	}

	/** 지운다. 태그, 문항 유형, 근거 숫자도 함께 지워진다. */
	@Transactional
	void delete(long userId, long cardId) {
		this.cards.delete(mine(userId, cardId));
	}

	/**
	 * 카드 확인. 확인한 것으로 두고(verified_at 은 지금), STAR 에만 있는 숫자를 그 문장째 근거 숫자에 더하고, 추출 안내를 비운다.
	 * 다시 확인해도 된다: 더할 숫자가 없으면 근거 숫자는 그대로고 확인한 시각만 바뀐다.
	 */
	@Transactional
	void verify(long userId, long cardId) {
		ExperienceCard card = mine(userId, cardId);
		card.verify(CardRules.factsToAdd(card.star(), card.facts()), this.clock.instant());
	}

	private ExperienceCard mine(long userId, long cardId) {
		return this.cards.findById(cardId)
			.filter((card) -> card.isOwnedBy(userId))
			.orElseThrow(() -> new ApiException(CardErrorCode.CARD_NOT_FOUND));
	}

}
