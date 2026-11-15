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
	 * @param link      프런트의 초대 수락 화면 주소(프런트 주소 + /invite/토큰, 프런트의 paths.invite). 토큰은 이 링크에만 있다
	 * @param expiresAt 만료 시각(한국 시각 +09:00)
	 */
	record InviteResponse(String link, OffsetDateTime expiresAt) {
	}

	/**
	 * GET /api/group-invites/{token} 의 응답. 로그인하지 않은 사람도 보므로 초대 화면에 쓰는 것만 싣는다(멤버 이름, id 는 없다).
	 *
	 * @param mark        그룹 표시 글자
	 * @param inviterName 링크를 만든 사람의 표시 이름. 그 사람이 계정을 지웠으면 null
	 * @param memberCount 지금 멤버 수
	 */
	record InvitePreviewResponse(String groupName, String mark, String inviterName, int memberCount) {
	}

	/**
	 * GET /api/groups/{groupId}/invites 의 한 줄. 토큰은 해시로만 있어 링크는 다시 보여 줄 수 없다.
	 *
	 * @param createdBy 링크를 만든 사람. 계정을 지웠으면 null
	 * @param createdAt 만든 시각(한국 시각 +09:00)
	 * @param expiresAt 만료 시각(한국 시각 +09:00)
	 */
	record LiveInviteResponse(long id, MemberResponse createdBy, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
	}

}
