package com.readme.group;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 그룹 API 가 받고 돌려주는 작은 모양들.
 */
final class GroupRequests {

	private GroupRequests() {
	}

	/**
	 * POST /api/groups 의 본문. 이름은 앞뒤 공백을 지운다. 표시 글자를 비우면 이름의 첫 글자를 쓴다.
	 * 길이 제한은 테이블 칸(이름 50자, 표시 글자 4자)과 같다.
	 */
	record CreateGroupRequest(@NotBlank @Size(max = 50) String name, @Size(max = 4) String mark) {
	}

	/** PUT /api/groups/{groupId}/me/disclosure 의 본문. "interest", "applied", "progress". */
	record DisclosureRequest(@NotNull Disclosure disclosure) {
	}

	/**
	 * POST /api/groups/{groupId}/invites 의 응답.
	 *
	 * @param link      프런트의 초대 수락 화면 주소(프런트 주소 + /invites/토큰). 토큰은 이 링크에만 있다
	 * @param expiresAt 만료 시각(한국 시각 +09:00)
	 */
	record InviteResponse(String link, OffsetDateTime expiresAt) {
	}

}
