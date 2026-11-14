package com.readme.auth;

import java.util.List;

/**
 * GET /api/me/withdrawal 의 응답. 탈퇴하면 지워질 것의 수와 내가 그룹장인 그룹. 설정 화면의 WithdrawInfo 와 같은 모양이다.
 *
 * @param cards    내 경험카드 수(experience_cards)
 * @param sources  내가 올린 자료 수(card_sources)
 * @param postings 내 공고 수(user_postings)
 * @param groups   내가 멤버인 그룹 수(그룹장인 그룹 포함)
 * @param leads    내가 그룹장인 그룹. 들어간 순서다
 */
record WithdrawalResponse(int cards, int sources, int postings, int groups, List<Lead> leads) {

	/**
	 * @param id        그룹 id. 프런트 타입에는 없다(이름이 같은 그룹을 가르는 데 쓴다)
	 * @param others    나 말고 멤버 수
	 * @param successor 그룹장을 이어받을 사람(나 말고 가장 먼저 들어온 멤버). 혼자인 그룹은 null 이고, 탈퇴하면 그룹이 지워진다
	 */
	record Lead(long id, String name, int others, Successor successor) {
	}

	record Successor(long id, String name) {
	}

}
