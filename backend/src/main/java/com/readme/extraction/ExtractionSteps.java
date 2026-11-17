package com.readme.extraction;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.readme.profile.ProfileDraft;
import com.readme.profile.Profiles;

/**
 * 추출 작업의 DB 쪽 걸음. 걸음마다 짧은 트랜잭션이다. LLM 호출은 걸음 사이(트랜잭션 밖)에서 ExtractionRunner 가 한다.
 */
@Component
class ExtractionSteps {

	private final CardExtractionJobRepository jobs;

	private final CardExtractionQueries queries;

	private final ApplicationEventPublisher events;

	private final Profiles profiles;

	private final Clock clock;

	ExtractionSteps(CardExtractionJobRepository jobs, CardExtractionQueries queries, ApplicationEventPublisher events,
			Profiles profiles, Clock clock) {
		this.jobs = jobs;
		this.queries = queries;
		this.events = events;
		this.profiles = profiles;
		this.clock = clock;
	}

	/**
	 * 돌릴 일. parts 는 자료 본문의 조각들이고, doneParts 다음 조각부터 한다.
	 *
	 * @param sourceName 파일 이름. 붙여넣은 글이면 null
	 */
	record Work(long jobId, long userId, long sourceId, String sourceName, List<String> parts, int doneParts) {
	}

	/** 작업을 돌기 시작한다. 이미 끝났거나(DONE, FAILED) 작업이나 자료가 지워졌으면 null. */
	@Transactional
	Work begin(long jobId) {
		CardExtractionJob job = this.jobs.findById(jobId).orElse(null);
		if (job == null || job.status().equals("DONE") || job.status().equals("FAILED")) {
			return null;
		}
		CardExtractionQueries.Source source = this.queries.source(job.sourceId()).orElse(null);
		if (source == null) {
			return null;
		}
		job.start(this.clock.instant());
		return new Work(jobId, job.userId(), job.sourceId(), source.name(), SourceParts.split(source.body()),
				job.doneParts());
	}

	/**
	 * 조각 하나의 답을 저장한다: 이미 있는 카드와 제목이 같은 카드는 건너뛰고(그 이미 있는 카드의 제목을 작업에 남긴다), 나머지는
	 * card 패키지가 저장하게 이벤트로 넘기고, 프로필의 빈 칸을 채우고, 마친 조각 수를 올린다. 작업이 지워졌으면(자료를 지웠다) 아무것도
	 * 하지 않는다.
	 *
	 * <p>shortcut: 프로필의 학력·자격증·어학·경력은 그 목록이 빈 때만 채워서, 조각마다 나뉘어 나온 줄은 처음 나온 조각의 것만 들어간다.
	 * 줄이 빠지는 일이 보이면 작업에 프로필 초안을 모아 끝에 한 번 넣는다.
	 */
	@Transactional
	void savePart(Work work, int part, ExtractionAnswer answer) {
		CardExtractionJob job = this.jobs.findById(work.jobId()).orElse(null);
		if (job == null) {
			return;
		}
		Map<String, String> seen = new HashMap<>();
		for (String title : this.queries.cardTitles(work.userId())) {
			seen.putIfAbsent(ExtractionPrompt.normalizeTitle(title), title);
		}
		List<ExtractedCard> fresh = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		for (ExtractedCard card : (answer.cards() != null) ? answer.cards() : List.<ExtractedCard>of()) {
			String key = (card != null && card.title() != null) ? ExtractionPrompt.normalizeTitle(card.title()) : "";
			if (key.isEmpty()) {
				continue;
			}
			String same = seen.putIfAbsent(key, card.title().strip());
			if (same != null) {
				skipped.add(same);
			}
			else {
				fresh.add(card);
			}
		}
		this.events.publishEvent(new CardsExtracted(work.userId(), work.sourceId(), work.jobId(), part,
				work.parts().get(part - 1), fresh));
		ProfileDraft draft = answer.profileDraft();
		if (draft != null) {
			this.profiles.fillBlanks(work.userId(), draft);
		}
		job.partDone(part, skipped);
	}

	@Transactional
	void finish(long jobId) {
		this.jobs.findById(jobId).ifPresent((job) -> job.done(this.clock.instant()));
	}

	@Transactional
	void fail(long jobId, String message) {
		this.jobs.findById(jobId).ifPresent((job) -> job.fail(message, this.clock.instant()));
	}

}
