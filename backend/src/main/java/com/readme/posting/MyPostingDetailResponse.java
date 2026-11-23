package com.readme.posting;

import java.util.List;

import com.readme.group.Disclosure;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고 상세. 목록의 한 줄에 공고 내용, 나만 보는 메모, 그룹마다의 내 모습이 더해진다. 프런트의 MyPostingDetail 모양이다.
 * 맞는 카드(matches, unmatched, requirements)는 공고 분석과 내 카드, 내 프로필로 센다(설계 4.8, CardMatching). 분석이 없으면 셋 다
 * 빈 목록이다.
 *
 * @param analysis     공고 분석(설계 4.7). 아직 없으면 null. 프런트의 PostingAnalysis 모양이다
 * @param memo        나만 보는 메모. 없으면 null
 * @param matches     요건이나 키워드를 하나라도 맞춘 내 카드(경험 이름, 제목, 겹친 낱말). 잘 맞는 것이 앞이다(점수는 보이지 않는다)
 * @param unmatched   카드로 답하지 못한 필수·우대 요건(프로필에 있는 요건은 빠진다)
 * @param requirements 필수·우대 요건마다의 결과(카드 있음, 프로필에 있음, 카드 없음, 판단 못 함)와 맞춘 카드마다 겹친 낱말
 * @param groupStates groups 와 같은 그룹들에 그 그룹에서의 내 관심, 댓글 수, 내 공개 범위가 붙는다
 * @param cardPriority 이 공고에 먼저 쓸 내 경험카드(카드 우선순위 설계). 첫째가 앞이다. 없으면 빈 목록
 * @param questions   자기소개서 문항과 문항마다 고른 카드(문항 매칭 설계). 저장 전이면 공고 분석의 문항이다(id 는 null, 카드는 빈 목록)
 */
record MyPostingDetailResponse(long id, String company, String title, DeadlineResponse deadline, Decision decision,
		Stage stage, Result result, String status, List<MyPostingResponse.Group> groups, String applyAt,
		String sourceUrl, List<PostSection> sections, List<String> keywords, PostingAnalysis analysis, String memo,
		List<CardMatching.CardMatch> matches, List<String> unmatched,
		List<CardMatching.RequirementResult> requirements, List<GroupState> groupStates,
		List<PriorityCard> cardPriority, List<Question> questions) {

	static MyPostingDetailResponse of(MyPostingResponse posting, Details details, List<String> keywords,
			CardMatching.Result match, List<GroupState> groupStates, List<PriorityCard> cardPriority,
			List<Question> questions) {
		return new MyPostingDetailResponse(posting.id(), posting.company(), posting.title(), posting.deadline(),
				posting.decision(), posting.stage(), posting.result(), posting.status(), posting.groups(),
				details.applyAt(), details.sourceUrl(), details.sections(), keywords, details.analysis(),
				details.memo(), match.matches(), match.unmatched(), match.requirements(), groupStates, cardPriority,
				questions);
	}

	/**
	 * 카드 우선순위 한 장. 프런트의 PriorityCard 모양이다.
	 *
	 * @param experience 경험 이름. 없으면 빈 글자
	 * @param reason     LLM 이 고른 이유. 사용자가 더한 카드는 null
	 */
	record PriorityCard(long cardId, String title, String experience, String reason) {
	}

	/**
	 * 자기소개서 문항 하나(문항 매칭 설계 4절).
	 *
	 * @param id    저장 전(공고 분석의 문항)이면 null
	 * @param limit 글자 수. 없으면 null
	 * @param type  LLM 이 고른 문항 유형(JOB, PROBLEM 등 아홉). 고르기 전이면 null
	 * @param cards 고른 카드. 0 이 주된 카드다
	 */
	record Question(Long id, String text, Integer limit, String type, List<QuestionCard> cards) {
	}

	/**
	 * 문항에 고른 카드 한 장.
	 *
	 * @param experience 경험 이름. 없으면 빈 글자
	 * @param reason     LLM 이 고른 이유. 사용자가 더한 카드는 null
	 * @param emphasis   살릴 칸(situation, task 등). 사용자가 더한 카드는 빈 목록
	 */
	record QuestionCard(long cardId, String title, String experience, String reason, List<String> emphasis) {
	}

	/** 상세에만 있는 공고 본체의 값과 내 메모, 내 문항 목록을 저장했는지. */
	record Details(String applyAt, String sourceUrl, List<PostSection> sections, PostingAnalysis analysis,
			String memo, boolean questionsSaved) {
	}

	/**
	 * 이 공고가 있는 내 그룹 하나에서의 내 모습. 프런트의 PostingGroupState 모양이다.
	 *
	 * @param interested   그 그룹에서 관심을 켰는지
	 * @param commentCount 그 그룹의 댓글 수
	 * @param disclosure   내가 그 그룹에 정한 공개 범위
	 */
	record GroupState(long id, String name, String mark, long postId, boolean interested, int commentCount,
			Disclosure disclosure) {

		MyPostingResponse.Group group() {
			return new MyPostingResponse.Group(this.id, this.name, this.mark, this.postId);
		}

	}

}
