package com.readme.group;

import java.util.List;

/**
 * GET /api/groups 의 한 항목. 프런트의 Group 모양이다.
 *
 * @param members       보는 사람을 포함한 멤버. 들어온 순서
 * @param leaderId      그룹장. 그룹장이 계정을 지우면 null 이다(설계 9절의 "그룹장이 없어질 때")
 * @param openPostCount 진행 중인 공고 수. 내린 공고와 마감된 공고를 뺀다
 * @param myDisclosure  보는 사람이 이 그룹에 정한 공개 범위
 */
public record GroupResponse(long id, String name, String mark, List<MemberResponse> members, Long leaderId,
		int openPostCount, Disclosure myDisclosure) {
}
