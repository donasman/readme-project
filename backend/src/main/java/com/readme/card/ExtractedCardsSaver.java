package com.readme.card;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.readme.card.CardRules.Normalized;
import com.readme.card.CardRules.SourceText;
import com.readme.extraction.CardsExtracted;
import com.readme.extraction.ExtractedCard;

/**
 * 추출 작업이 뽑은 카드를 저장한다(경험카드 설계 4.6). 카드 테이블의 주인이라 extraction 이 낸 이벤트를 받는다.
 * 내는 쪽(추출 작업이 조각 하나를 마치는 트랜잭션)에서 바로 돈다. 실패하면 그 조각이 통째로 되돌려진다.
 */
@Component
class ExtractedCardsSaver {

	private final ExperienceCardRepository cards;

	private final Clock clock;

	ExtractedCardsSaver(ExperienceCardRepository cards, Clock clock) {
		this.cards = cards;
		this.clock = clock;
	}

	@EventListener
	void save(CardsExtracted event) {
		SourceText source = SourceText.of(event.sourceText());
		Instant now = this.clock.instant();
		for (ExtractedCard raw : event.cards()) {
			Normalized card = CardRules.normalizeExtracted(raw, source);
			if (card != null) {
				this.cards.save(new ExperienceCard(event.userId(), card.card(), event.sourceId(), event.jobId(),
						event.part(), card.notes(), now));
			}
		}
	}

}
