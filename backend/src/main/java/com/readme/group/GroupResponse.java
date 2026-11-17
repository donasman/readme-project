package com.readme.group;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/groups 의 한 항목. 프런트의 Group 모양이다.
 *
 * @param members         보는 사람을 포함한 멤버. 들어온 순서
 * @param leaderId        그룹장. 그룹장이 계정을 지우면 null 이다(설계 9절의 "그룹장이 없어질 때")
 * @param openPostCount   진행 중인 공고 수. 내린 공고와 마감된 공고를 뺀다
 * @param myDisclosure    보는 사람이 이 그룹에 정한 공개 범위
 * @param nearestDeadline 내리지 않은 공고 가운데 마감일이 오늘(한국 날짜) 이후로 가장 가까운 것. 없으면 null
 */
public record GroupResponse(long id, String name, String mark, List<GroupMemberResponse> members, Long leaderId,
		int openPostCount, Disclosure myDisclosure, NearestDeadline nearestDeadline) {

	/**
	 * 그룹 안의 멤버. 프런트의 GroupMember 모양이다.
	 *
	 * @param name     표시 이름(users.display_name)
	 * @param joinedAt 들어온 날(한국 날짜)
	 */
	record GroupMemberResponse(long id, String name, LocalDate joinedAt) {
	}

	/** 내 그룹 화면의 "가장 가까운 마감": 날짜와 그 공고의 회사, 제목. */
	record NearestDeadline(LocalDate date, String company, String title) {
	}

}
