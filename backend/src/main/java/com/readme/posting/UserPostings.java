package com.readme.posting;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.error.ApiException;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고(user_postings)를 바꾸는 곳. 다른 기능은 이 클래스로만 내 공고 행을 만들고 고친다.
 */
@Service
public class UserPostings {

	private final UserPostingRepository repository;

	private final Postings postings;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	UserPostings(UserPostingRepository repository, Postings postings, Clock clock, ApplicationEventPublisher events) {
		this.repository = repository;
		this.postings = postings;
		this.clock = clock;
		this.events = events;
	}

	/**
	 * 그룹 없이 내 공고에 넣는다(설계 4.2). 같은 공고(공고키가 같은 공고)가 있으면 그 공고에 내 행을 만들고, 그 공고의 값은
	 * 고치지 않는다. 내 행은 관심을 켤 때처럼 미정으로 시작한다. 넣은 공고의 id 를 돌려준다.
	 * 이미 내 공고에 있으면 409 ALREADY_IN_MY_POSTINGS 와 그 공고의 postingId. 공고키가 있는 공고는 두 번 눌러도 행이 하나다.
	 * 키를 만들 수 없는 아주 긴 주소의 공고는 같은 공고로 알아보지 못하므로 넣을 때마다 새 공고와 새 행이 생긴다.
	 */
	@Transactional
	long add(long userId, PostingDraft draft) {
		long postingId = this.postings.findOrCreate(draft, userId);
		if (this.repository.insertIfAbsent(userId, postingId) == 0) {
			throw new ApiException(PostingErrorCode.ALREADY_IN_MY_POSTINGS).with("postingId", postingId);
		}
		return postingId;
	}

	/** 내 공고 목록에 이 공고를 넣는다. 이미 있으면 그대로 둔다(상태를 지우지 않는다). */
	@Transactional
	public void addIfAbsent(long userId, long postingId) {
		this.repository.insertIfAbsent(userId, postingId);
	}

	/**
	 * 메일에서 읽은 공고를 내 공고에 넣는다(메일 설계 M11). 같은 공고(공고키)가 있으면 그 공고를 쓴다. 내 행은 미정으로 시작한다.
	 * 이미 내 공고에 있으면 그대로 두고 created 가 false 다.
	 *
	 * @param deadline 메일에서 읽은 마감일. 없으면 null(마감을 모르는 공고)
	 */
	@Transactional
	public Added addFromMail(long userId, String company, String title, LocalDate deadline) {
		PostingDraft draft = new PostingDraft(company, title, (deadline != null) ? DeadlineKind.DATE : DeadlineKind.UNKNOWN,
				deadline, null, null, null, List.of(), List.of());
		long postingId = this.postings.findOrCreate(draft, userId);
		return new Added(postingId, this.repository.insertIfAbsent(userId, postingId) == 1);
	}

	/** {@link #addFromMail} 의 결과. created 는 이번에 내 공고 행을 새로 만들었는지다. */
	public record Added(long postingId, boolean created) {
	}

	/**
	 * 내 공고에서 뺀다(공고 설계 4.2, 2026-11-16 사용자 결정). 지원 상태, 메모, 전형 일정도 함께 지워지고, 내 그룹들에서 켜 둔
	 * 그 공고의 관심도 끈다({@link MyPostingRemoved} 를 grouppost 가 받는다). 공고 본체, 그룹 공고, 댓글은 남긴다.
	 * 내 공고 빼기와 메일로 넣은 공고 되돌리기(메일 설계 M12)가 부른다. 내 공고에 없었으면 false.
	 */
	@Transactional
	public boolean remove(long userId, long postingId) {
		if (this.repository.deleteRow(userId, postingId) == 0) {
			return false;
		}
		this.events.publishEvent(new MyPostingRemoved(userId, postingId));
		return true;
	}

	/**
	 * 전형 일정(user_posting_schedules)을 둔다. 같은 단계의 일정이 있으면 바꾼다(메일 설계 M13). 내 공고 행이 있어야 한다.
	 *
	 * @param time 없으면 null
	 */
	@Transactional
	public void setSchedule(long userId, long postingId, Stage stage, LocalDate date, LocalTime time) {
		this.repository.upsertSchedule(userId, postingId, stage.name(), date, time, this.clock.instant());
	}

	/**
	 * 내 지원 상태를 바꾼다. 내 공고 행이 없으면 만든다. 값의 짝은 부르는 쪽이 확인한다({@link ApplicationStatus#isConsistent}).
	 * 메일 확인함의 반영도 이것을 부른다.
	 */
	@Transactional
	public void setStatus(long userId, long postingId, ApplicationStatus status) {
		this.repository.upsertStatus(userId, postingId, status.decision().name(),
				(status.stage() != null) ? status.stage().name() : null,
				(status.result() != null) ? status.result().name() : null, this.clock.instant());
	}

	/** 나만 보는 메모를 바꾼다. 빈 메모는 null 로 둔다. 내 공고에 없는 공고면 false. */
	@Transactional
	boolean setMemo(long userId, long postingId, String memo) {
		String stored = (memo == null || memo.isBlank()) ? null : memo;
		return this.repository.updateMemo(userId, postingId, stored, this.clock.instant()) == 1;
	}

}
