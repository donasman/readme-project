package com.readme.posting;

import java.util.List;

import com.readme.group.Disclosure;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고 상세. 목록의 한 줄에 공고 내용, 나만 보는 메모, 그룹마다의 내 모습이 더해진다. 프런트의 MyPostingDetail 모양이다.
 * 맞는 카드(matches, unmatched)는 공고 분석과 내 카드로 센다(설계 4.8, CardMatching). 분석이 없으면 둘 다 빈 목록이다.
 *
 * @param analysis    공고 분석(설계 4.7). 아직 없으면 null. 프런트 타입에는 아직 없다(설계 8절 30번)
 * @param memo        나만 보는 메모. 없으면 null
 * @param matches     요건이나 키워드를 하나라도 맞춘 내 카드. 잘 맞는 것이 앞이다(점수는 보이지 않는다)
 * @param unmatched   카드로 답하지 못한 필수·우대 요건
 * @param groupStates groups 와 같은 그룹들에 그 그룹에서의 내 관심, 댓글 수, 내 공개 범위가 붙는다
 */
record MyPostingDetailResponse(long id, String company, String title, DeadlineResponse deadline, Decision decision,
		Stage stage, Result result, String status, List<MyPostingResponse.Group> groups, String applyAt,
		String sourceUrl, List<PostSection> sections, List<String> keywords, PostingAnalysis analysis, String memo,
		List<CardMatching.CardMatch> matches, List<String> unmatched, List<GroupState> groupStates) {

	static MyPostingDetailResponse of(MyPostingResponse posting, Details details, List<String> keywords,
			CardMatching.Result match, List<GroupState> groupStates) {
		return new MyPostingDetailResponse(posting.id(), posting.company(), posting.title(), posting.deadline(),
				posting.decision(), posting.stage(), posting.result(), posting.status(), posting.groups(),
				details.applyAt(), details.sourceUrl(), details.sections(), keywords, details.analysis(),
				details.memo(), match.matches(), match.unmatched(), groupStates);
	}

	/** 상세에만 있는 공고 본체의 값과 내 메모. */
	record Details(String applyAt, String sourceUrl, List<PostSection> sections, PostingAnalysis analysis,
			String memo) {
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
