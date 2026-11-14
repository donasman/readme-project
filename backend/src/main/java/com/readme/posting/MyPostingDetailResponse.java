package com.readme.posting;

import java.util.List;

import com.readme.group.Disclosure;
import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고 상세. 목록의 한 줄에 공고 내용, 나만 보는 메모, 그룹마다의 내 모습이 더해진다. 프런트의 MyPostingDetail 모양이다.
 * 맞는 카드(matches, unmatched)는 아직 없다(화면·API 대응 문서 3절 13번).
 *
 * @param memo        나만 보는 메모. 없으면 null
 * @param groupStates groups 와 같은 그룹들에 그 그룹에서의 내 관심, 댓글 수, 내 공개 범위가 붙는다
 */
record MyPostingDetailResponse(long id, String company, String title, DeadlineResponse deadline, Decision decision,
		Stage stage, Result result, String status, List<MyPostingResponse.Group> groups, String applyAt,
		String sourceUrl, List<PostSection> sections, List<String> keywords, String memo,
		List<GroupState> groupStates) {

	static MyPostingDetailResponse of(MyPostingResponse posting, Details details, List<String> keywords,
			List<GroupState> groupStates) {
		return new MyPostingDetailResponse(posting.id(), posting.company(), posting.title(), posting.deadline(),
				posting.decision(), posting.stage(), posting.result(), posting.status(), posting.groups(),
				details.applyAt(), details.sourceUrl(), details.sections(), keywords, details.memo(), groupStates);
	}

	/** 상세에만 있는 공고 본체의 값과 내 메모. */
	record Details(String applyAt, String sourceUrl, List<PostSection> sections, String memo) {
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
