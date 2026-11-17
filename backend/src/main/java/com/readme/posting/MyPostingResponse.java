package com.readme.posting;

import java.util.List;

import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고 목록의 한 줄. 프런트의 MyPosting 모양에 상태 글자(status)가 더해진다.
 *
 * @param id     공고 본체(postings)의 id
 * @param stage  지원함일 때만 있다
 * @param result 지원함일 때만 있다
 * @param status 화면 글자(설계 5.3). 그룹 공고의 me.status 와 같다. 지원함이 아니면 null
 * @param groups 이 공고가 올라가 있는(내리지 않은) 내 그룹. 내가 들어간 순서
 */
record MyPostingResponse(long id, String company, String title, DeadlineResponse deadline, Decision decision,
		Stage stage, Result result, String status, List<Group> groups) {

	/**
	 * 프런트의 PostingGroup 모양.
	 *
	 * @param postId 그 그룹 공고의 주소(/groups/{id}/posts/{postId})에 쓰는 값. 서버에서는 공고 id 와 같다
	 */
	record Group(long id, String name, String mark, long postId) {
	}

}
