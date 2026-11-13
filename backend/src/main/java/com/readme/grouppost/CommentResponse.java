package com.readme.grouppost;

import java.time.OffsetDateTime;
import java.util.List;

import com.readme.group.MemberResponse;

/**
 * 댓글 하나. 프런트의 Comment 모양이다.
 *
 * @param author    쓴 사람. 계정을 지웠으면 null
 * @param createdAt 한국 시각(+09:00)의 ISO 8601
 */
record CommentResponse(long id, MemberResponse author, OffsetDateTime createdAt, List<CommentPart> body) {
}
