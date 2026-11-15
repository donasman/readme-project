package com.readme.posting;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import tools.jackson.databind.json.JsonMapper;

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

	private final JsonMapper jsonMapper;

	Postings(PostingRepository repository, Clock clock, JsonMapper jsonMapper) {
		this.repository = repository;
		this.clock = clock;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * 같은 공고가 이미 있으면 그 공고의 id 를, 없으면 새로 만들어 id 를 돌려준다. 같은 공고는 공고키로 알아본다(설계 가정 3).
	 * 이미 있는 공고의 값은 고치지 않는다. 처음 올린 사람이 넣은 값이 남는다. 공고 내용 묶음과 키워드도 새로 만들 때만 넣는다.
	 */
	@Transactional
	public long findOrCreate(PostingDraft draft, long userId) {
		Instant now = this.clock.instant();
		String key = draft.key().key();
		if (key.length() > MAX_KEY_LENGTH) {
			return withContent(this.repository.save(new Posting(draft, null, userId, now)).getId(), draft);
		}
		Optional<Long> found = this.repository.findIdByKey(key);
		if (found.isPresent()) {
			return found.get();
		}
		boolean created = this.repository.insertIfKeyIsNew(key, draft.company(), draft.title(),
				draft.deadlineKind().name(), draft.deadlineDate(), draft.deadlineName(), draft.applyAt(),
				draft.sourceUrl(), userId, now) == 1;
		long postingId = this.repository.findIdByKey(key).orElseThrow();
		return created ? withContent(postingId, draft) : postingId;
	}

	/** 막 만든 공고에 공고 내용 묶음(postings.sections)과 키워드(posting_keywords, 넣은 순서가 seq)를 넣는다. */
	private long withContent(long postingId, PostingDraft draft) {
		if (!draft.sections().isEmpty()) {
			this.repository.setSections(postingId, this.jsonMapper.writeValueAsString(draft.sections()));
		}
		if (!draft.keywords().isEmpty()) {
			this.repository.insertKeywords(postingId, this.jsonMapper.writeValueAsString(draft.keywords()));
		}
		return postingId;
	}

	/**
	 * 날짜 넣기. 마감을 읽지 못한(UNKNOWN) 공고에만 한 번, 날짜(DATE)나 상시(ALWAYS)를 넣는다.
	 * 넣었으면 true, 이미 마감이 있었으면 false. 공고 본체를 고치므로 같은 공고를 올린 다른 그룹에도 보인다(설계 가정 2).
	 *
	 * @param date DATE 일 때만 넣고, ALWAYS 면 null
	 */
	@Transactional
	public boolean fillUnknownDeadline(long postingId, DeadlineKind kind, LocalDate date, long userId) {
		requireDateOrAlways(kind, date);
		String newKey = this.repository.findById(postingId).map((posting) -> keyWithDate(posting, date)).orElse(null);
		return this.repository.fillUnknownDeadline(postingId, kind.name(), date, newKey, userId,
				this.clock.instant()) == 1;
	}

	/**
	 * 공고 고치기(설계 4.5). 회사명, 공고명, 마감을 이 값으로 바꾼다. 날짜 넣기와 달리 마감이 이미 있어도 바꾼다.
	 * UNKNOWN 은 마감이 아직 UNKNOWN 일 때만 받는다(회사명만 고칠 때). 마감이 있는 공고를 UNKNOWN 으로 되돌리려 하면
	 * 고치지 않고 false 를 돌려준다. 공고 본체를 고치므로 같은 공고를 올린 다른 그룹에도 보인다(설계 가정 2).
	 * 누가 고칠 수 있는지는 부르는 쪽이 본다.
	 *
	 * <p>shortcut: 원문 주소와 공고키는 바꾸지 않아, 링크 없는 공고(local: 키)는 고치기 전 값으로 만든 키가 남는다.
	 * 고친 값으로 다시 올리면 다른 공고가 된다. 같은 공고로 알아봐야 하면 날짜 넣기처럼 키를 다시 만든다.
	 *
	 * @param date DATE 일 때만 넣고, 그 밖에는 null
	 */
	@Transactional
	public boolean edit(long postingId, String company, String title, DeadlineKind kind, LocalDate date) {
		if ((kind == DeadlineKind.DATE) != (date != null)) {
			throw new IllegalArgumentException("날짜는 DATE 일 때만, 꼭 넣는다: " + kind + ", " + date);
		}
		return this.repository.edit(postingId, company, title, kind.name(), date, this.clock.instant()) == 1;
	}

	private static void requireDateOrAlways(DeadlineKind kind, LocalDate date) {
		if (kind == DeadlineKind.UNKNOWN || (kind == DeadlineKind.DATE) != (date != null)) {
			throw new IllegalArgumentException("마감은 날짜가 있는 DATE 나 날짜가 없는 ALWAYS 로만 넣는다: " + kind + ", " + date);
		}
	}

	/**
	 * 링크 없는 공고의 키는 회사명|공고명|마감일로 만든다(local:). 날짜가 들어가면 그 날짜로 다시 만들어야, 같은 날짜로
	 * 올라오는 같은 공고를 알아본다. 링크로 만든 키와 상시(날짜 없음)는 바뀌지 않으므로 null.
	 */
	private static String keyWithDate(Posting posting, LocalDate date) {
		if (date == null || posting.postingKey() == null || !posting.postingKey().startsWith("local:")) {
			return null;
		}
		return PostingKeys.derive(null, posting.company(), posting.title(), date.toString()).key();
	}

}
