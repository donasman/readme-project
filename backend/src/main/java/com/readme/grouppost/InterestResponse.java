package com.readme.grouppost;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.readme.group.Disclosure;
import com.readme.group.MemberResponse;
import com.readme.posting.ApplicationStatus;

/**
 * 관심을 켠 다른 멤버 한 명. 프런트의 Interest 모양이다.
 *
 * @param applied  지원함 표시. 그 멤버의 공개 범위가 "지원 여부까지" 이상일 때만 true 가 될 수 있다
 * @param progress 단계·결과 글자. 그 멤버의 공개 범위가 "단계·결과까지"일 때만 있다
 */
record InterestResponse(MemberResponse member, boolean applied,
		@JsonInclude(JsonInclude.Include.NON_NULL) String progress) {

	/**
	 * 그 멤버의 공개 범위만큼만 지원 상태를 싣는다(설계 5.2). 남에게 보이면 안 되는 값은 여기서 잘라 응답에 넣지 않는다.
	 *
	 * @param disclosure 그 멤버가 이 그룹에 정한 공개 범위
	 * @param status     그 멤버의 이 공고 지원 상태. 없으면 null
	 */
	static InterestResponse of(MemberResponse member, Disclosure disclosure, ApplicationStatus status) {
		boolean applied = disclosure != Disclosure.INTEREST && status != null && status.applied();
		String progress = (disclosure == Disclosure.PROGRESS && status != null) ? status.progressLabel() : null;
		return new InterestResponse(member, applied, progress);
	}

}
