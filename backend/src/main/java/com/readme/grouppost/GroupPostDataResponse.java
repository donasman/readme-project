package com.readme.grouppost;

import java.time.LocalDate;

/**
 * GET …/posts/{postId} 의 응답. 프런트의 GroupPostData 모양이다.
 */
record GroupPostDataResponse(LocalDate today, GroupPostDetailResponse post) {
}
