package com.readme.posting;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고 본체(postings)를 바꾸는 곳. 다른 기능은 이 클래스로만 공고 본체를 만들고 고친다.
 */
@Service
public class Postings {

	/** posting_key 칸의 길이. 이보다 긴 키(아주 긴 주소)는 만들 수 없는 것으로 보고 null 로 둔다(설계 3.1 의 칸 설명). */
	static final int MAX_KEY_LENGTH = 300;

	private final PostingRepository repository;

	private final Clock clock;

	Postings(PostingRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * 같은 공고가 이미 있으면 그 공고의 id 를, 없으면 새로 만들어 id 를 돌려준다. 같은 공고는 공고키로 알아본다(설계 가정 3).
	 * 이미 있는 공고의 값은 고치지 않는다. 처음 올린 사람이 넣은 값이 남는다.
	 */
	@Transactional
	public long findOrCreate(PostingDraft draft, long userId) {
		Instant now = this.clock.instant();
		String key = draft.key().key();
		if (key.length() > MAX_KEY_LENGTH) {
			return this.repository.save(new Posting(draft, null, userId, now)).getId();
		}
		return this.repository.findIdByKey(key).orElseGet(() -> {
			this.repository.insertIfKeyIsNew(key, draft.company(), draft.title(), draft.deadlineKind().name(),
					draft.deadlineDate(), draft.deadlineName(), draft.applyAt(), draft.sourceUrl(), userId, now);
			return this.repository.findIdByKey(key).orElseThrow();
		});
	}

	/**
	 * 날짜 넣기. 마감을 읽지 못한(UNKNOWN) 공고에만 한 번, 날짜(DATE)나 상시(ALWAYS)를 넣는다.
	 * 넣었으면 true, 이미 마감이 있었으면 false. 공고 본체를 고치므로 같은 공고를 올린 다른 그룹에도 보인다(설계 가정 2).
	 *
	 * @param date DATE 일 때만 넣고, ALWAYS 면 null
	 */
	@Transactional
	public boolean fillUnknownDeadline(long postingId, DeadlineKind kind, LocalDate date, long userId) {
		if (kind == DeadlineKind.UNKNOWN || (kind == DeadlineKind.DATE) != (date != null)) {
			throw new IllegalArgumentException("마감은 날짜가 있는 DATE 나 날짜가 없는 ALWAYS 로만 넣는다: " + kind + ", " + date);
		}
		return this.repository.fillUnknownDeadline(postingId, kind.name(), date, userId, this.clock.instant()) == 1;
	}

}
