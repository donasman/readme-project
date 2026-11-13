package com.readme.grouppost;

import java.time.LocalDate;
import java.util.List;

/**
 * GET …/posts 의 응답. 프런트의 GroupPostsData 모양이다.
 *
 * @param today 서버의 한국 날짜. 프런트가 D-day, 마감됨, 새 공고를 이 날로 센다
 */
record GroupPostsResponse(LocalDate today, List<GroupPostResponse> posts) {
}
