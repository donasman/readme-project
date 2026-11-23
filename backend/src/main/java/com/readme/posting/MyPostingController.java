package com.readme.posting;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고(설계 4.2): 목록, 그룹 없이 넣기, 상세, 지원 상태 바꾸기, 나만 보는 메모. 조회와 메모는 내 공고 행(user_postings)이 있는 공고만 다룬다.
 */
@RestController
class MyPostingController {

	private final UserPostings userPostings;

	private final CardPriorities cardPriorities;

	private final PostingQuestions postingQuestions;

	private final MyPostingQueries queries;

	private final JdbcClient jdbc;

	private final Clock clock;

	MyPostingController(UserPostings userPostings, CardPriorities cardPriorities, PostingQuestions postingQuestions,
			MyPostingQueries queries, JdbcClient jdbc, Clock clock) {
		this.userPostings = userPostings;
		this.cardPriorities = cardPriorities;
		this.postingQuestions = postingQuestions;
		this.queries = queries;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** 내 공고 전부와 서버 기준 오늘. 거르기와 검색은 프런트가 한다. */
	@GetMapping("/api/me/postings")
	PostingsResponse list(@CurrentUser LoginUser user) {
		return new PostingsResponse(LocalDate.now(this.clock), this.queries.list(user.id()));
	}

	/**
	 * 그룹 없이 내 공고에 넣기. 본문은 그룹에 올리기와 같다. 넣은 공고를 상세와 같은 모양으로 돌려준다.
	 * 이미 내 공고에 있으면 409 ALREADY_IN_MY_POSTINGS 와 postingId.
	 */
	@PostMapping("/api/me/postings")
	@ResponseStatus(HttpStatus.CREATED)
	PostingResponse add(@Valid @RequestBody UploadRequest request, @CurrentUser LoginUser user) {
		long postingId = this.userPostings.add(user.id(), request.toDraft());
		return detail(postingId, user);
	}

	/** 내 전형 일정 전부(메일 설계 4.5). 지금은 메일 확인함에서 반영한 일정만 있다. */
	@GetMapping("/api/me/schedules")
	List<ScheduleResponse> schedules(@CurrentUser LoginUser user) {
		return this.queries.schedules(user.id());
	}

	/** 내 공고 상세. 내 공고에 없는 공고면 404 POSTING_NOT_FOUND. */
	@GetMapping("/api/me/postings/{postingId}")
	PostingResponse detail(@PathVariable long postingId, @CurrentUser LoginUser user) {
		MyPostingDetailResponse posting = this.queries.detail(user.id(), postingId)
			.orElseThrow(() -> new ApiException(PostingErrorCode.POSTING_NOT_FOUND));
		return new PostingResponse(LocalDate.now(this.clock), posting);
	}

	/**
	 * 내 지원 상태 바꾸기. 내 그룹에 올라온 공고이거나 이미 내 공고에 있는 공고만 바꿀 수 있다. 아니면 404 POSTING_NOT_FOUND.
	 * 단계와 결과는 지원함일 때만, 둘을 함께 넣는다. 아니면 400 INVALID_INPUT. 메모는 건드리지 않는다.
	 */
	@PutMapping("/api/me/postings/{postingId}")
	StatusResponse setStatus(@PathVariable long postingId, @Valid @RequestBody StatusRequest request,
			@CurrentUser LoginUser user) {
		ApplicationStatus status = new ApplicationStatus(request.decision(), request.stage(), request.result());
		if (!status.isConsistent()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "단계와 결과는 지원함일 때만, 둘을 함께 넣어 주세요");
		}
		if (!canSee(user.id(), postingId)) {
			throw new ApiException(PostingErrorCode.POSTING_NOT_FOUND);
		}
		this.userPostings.setStatus(user.id(), postingId, status);
		return new StatusResponse(postingId, status.decision(), status.stage(), status.result(), status.label());
	}

	/**
	 * 내 공고에서 빼기(설계 4.2). 지원 상태, 메모, 전형 일정을 지우고 내 그룹들에서 켜 둔 이 공고의 관심을 끈다. 공고 본체와 그룹 공고는
	 * 남는다. 내 공고에 없는 공고면 404 POSTING_NOT_FOUND.
	 */
	@DeleteMapping("/api/me/postings/{postingId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable long postingId, @CurrentUser LoginUser user) {
		if (!this.userPostings.remove(user.id(), postingId)) {
			throw new ApiException(PostingErrorCode.POSTING_NOT_FOUND);
		}
	}

	/**
	 * 나만 보는 메모. 빈 메모는 지운다(null 로 둔다). 지원 상태는 건드리지 않는다.
	 * 내 공고에 없는 공고면 404 POSTING_NOT_FOUND.
	 */
	@PutMapping("/api/me/postings/{postingId}/memo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setMemo(@PathVariable long postingId, @Valid @RequestBody MemoRequest request, @CurrentUser LoginUser user) {
		if (!this.userPostings.setMemo(user.id(), postingId, request.memo())) {
			throw new ApiException(PostingErrorCode.POSTING_NOT_FOUND);
		}
	}

	/**
	 * 경험카드 우선순위 초안(카드 우선순위 설계). LLM 이 내 카드에서 5장까지 고르고 이유를 붙여 앞 목록을 바꾼다. 새 목록을 돌려준다.
	 * 내 공고에 없으면 404 POSTING_NOT_FOUND, 카드가 없으면 409 NO_CARDS, 공고 내용이 없으면 409 NO_POSTING_CONTENT,
	 * 한도에 닿았으면 429 LLM_LIMIT_REACHED, 답을 못 받으면 503 LLM_UNAVAILABLE, 쓸 수 없는 답이면 502 LLM_FAILED.
	 */
	@PostMapping("/api/me/postings/{postingId}/card-priority/draft")
	CardPriorityResponse draftCardPriority(@PathVariable long postingId, @CurrentUser LoginUser user) {
		this.cardPriorities.draft(user.id(), postingId);
		return new CardPriorityResponse(this.queries.cardPriority(user.id(), postingId));
	}

	/**
	 * 경험카드 우선순위를 이 순서로 둔다(5장까지, 빈 목록이면 비운다). 앞 목록에 있던 카드의 이유는 남는다. 새 목록을 돌려준다.
	 * 겹친 카드나 내 카드가 아닌 카드는 400 INVALID_INPUT, 내 공고에 없으면 404 POSTING_NOT_FOUND.
	 */
	@PutMapping("/api/me/postings/{postingId}/card-priority")
	CardPriorityResponse setCardPriority(@PathVariable long postingId, @Valid @RequestBody CardPriorityRequest request,
			@CurrentUser LoginUser user) {
		this.cardPriorities.reorder(user.id(), postingId, request.cardIds());
		return new CardPriorityResponse(this.queries.cardPriority(user.id(), postingId));
	}

	/**
	 * 자기소개서 문항 목록을 이 순서로 통째로 둔다(문항 매칭 설계 4절). id 가 있으면 그 문항을 고쳐 카드와 유형을 남기고, 없으면 새
	 * 문항이다. 빠진 문항은 지운다. 새 목록을 돌려준다. 10개를 넘거나 빈 글, 1,000자 넘는 글, 1~10,000 밖의 글자 수, 이 공고의 내
	 * 문항이 아닌 id 는 400 INVALID_INPUT, 내 공고에 없으면 404 POSTING_NOT_FOUND.
	 */
	@PutMapping("/api/me/postings/{postingId}/questions")
	QuestionsResponse setQuestions(@PathVariable long postingId, @Valid @RequestBody QuestionsRequest request,
			@CurrentUser LoginUser user) {
		this.postingQuestions.replace(user.id(), postingId, request.questions());
		return new QuestionsResponse(this.queries.questions(user.id(), postingId));
	}

	/**
	 * 문항마다 LLM 이 유형과 카드(2장까지, 500자 아래 문항은 1장)를 골라 앞 결과를 바꾼다. 저장 전이면 공고 분석의 문항을 먼저 저장한다.
	 * 새 목록을 돌려준다. 내 공고에 없으면 404 POSTING_NOT_FOUND, 문항이 없으면 409 NO_QUESTIONS, 카드가 없으면 409 NO_CARDS,
	 * 한도에 닿았으면 429 LLM_LIMIT_REACHED, 답을 못 받으면 503 LLM_UNAVAILABLE, 쓸 수 없는 답이면 502 LLM_FAILED.
	 */
	@PostMapping("/api/me/postings/{postingId}/questions/match")
	QuestionsResponse matchQuestions(@PathVariable long postingId, @CurrentUser LoginUser user) {
		this.postingQuestions.match(user.id(), postingId);
		return new QuestionsResponse(this.queries.questions(user.id(), postingId));
	}

	/**
	 * 문항 하나의 카드를 이 순서로 둔다(빈 목록이면 비운다). 앞에 있던 카드는 이유와 살릴 칸이 남는다. 새 목록을 돌려준다. 수를 넘거나
	 * 겹치거나 내 카드가 아닌 id 는 400 INVALID_INPUT, 이 공고의 내 문항이 아니면 404 QUESTION_NOT_FOUND, 내 공고에 없으면 404
	 * POSTING_NOT_FOUND.
	 */
	@PutMapping("/api/me/postings/{postingId}/questions/{questionId}/cards")
	QuestionsResponse setQuestionCards(@PathVariable long postingId, @PathVariable long questionId,
			@Valid @RequestBody QuestionCardsRequest request, @CurrentUser LoginUser user) {
		this.postingQuestions.setCards(user.id(), postingId, questionId, request.cardIds());
		return new QuestionsResponse(this.queries.questions(user.id(), postingId));
	}

	/** 문항 목록 두기의 본문. 첫째가 앞이다. */
	record QuestionsRequest(
			@NotNull @Size(max = PostingQuestions.MAX_QUESTIONS) List<PostingQuestions.@NotNull @Valid Input> questions) {
	}

	/** 문항 카드 바꾸기의 본문. 첫째가 주된 카드다. */
	record QuestionCardsRequest(@NotNull @Size(max = PostingQuestions.MAX_CARDS) List<@NotNull Long> cardIds) {
	}

	/** 문항 API 들의 응답. 상세의 questions 와 같은 모양이다. */
	record QuestionsResponse(List<MyPostingDetailResponse.Question> questions) {
	}

	/** 카드 우선순위 바꾸기의 본문. 첫째가 앞이다. */
	record CardPriorityRequest(@NotNull @Size(max = CardPriorities.MAX) List<@NotNull Long> cardIds) {
	}

	/** 카드 우선순위 초안과 바꾸기의 응답. */
	record CardPriorityResponse(List<MyPostingDetailResponse.PriorityCard> cards) {
	}

	private boolean canSee(long userId, long postingId) {
		return this.jdbc.sql("""
				select exists (select 1 from user_postings where user_id = :userId and posting_id = :postingId)
				    or exists (select 1
				                 from group_posts gp
				                 join group_members m on m.group_id = gp.group_id
				                where gp.posting_id = :postingId and m.user_id = :userId and gp.taken_down_at is null)
				""")
			.param("userId", userId)
			.param("postingId", postingId)
			.query(Boolean.class)
			.single();
	}

	/**
	 * GET /api/me/postings 의 응답. 프런트 getMyPostings 의 모양이다.
	 *
	 * @param today 서버의 한국 날짜. 프런트가 D-day 와 마감됨을 이 날로 센다
	 */
	record PostingsResponse(LocalDate today, List<MyPostingResponse> postings) {
	}

	/** GET /api/me/postings/{postingId} 의 응답. 프런트 getMyPosting 의 모양이다. */
	record PostingResponse(LocalDate today, MyPostingDetailResponse posting) {
	}

	/** PUT /api/me/postings/{postingId} 의 본문. 예: { "decision": "applied", "stage": "test", "result": "in_progress" } */
	record StatusRequest(@NotNull Decision decision, Stage stage, Result result) {
	}

	/**
	 * 바뀐 내 지원 상태.
	 *
	 * @param status 화면 글자(설계 5.3). 그룹 공고의 me.status 와 같다. 지원함이 아니면 null
	 */
	record StatusResponse(long postingId, Decision decision, Stage stage, Result result, String status) {
	}

	/** PUT /api/me/postings/{postingId}/memo 의 본문. 길이는 user_postings.memo 칸과 같다. 비었거나 null 이면 메모를 지운다. */
	record MemoRequest(@Size(max = 2000) String memo) {
	}

}
