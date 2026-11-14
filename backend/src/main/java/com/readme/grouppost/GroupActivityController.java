package com.readme.grouppost;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.config.TimeConfig;

/**
 * 홈의 그룹 소식(설계 4.5, 화면·API 대응 문서 3절 14번). 내 그룹의 최근 7일 새 공고와 새 댓글이다.
 */
@RestController
class GroupActivityController {

	/** 오늘과 그 앞 7일(한국 날짜). 프런트의 "3일 이내"(새 공고), "7일 안에 마감"과 같은 셈이다. */
	private static final int DAYS = 7;

	private static final int LIMIT = 20;

	private final GroupPostQueries queries;

	private final Clock clock;

	GroupActivityController(GroupPostQueries queries, Clock clock) {
		this.queries = queries;
		this.clock = clock;
	}

	/**
	 * 최근 것부터 20줄. 본 시각을 담는 칸이 없어 "마지막으로 본 뒤"가 아니라 늘 최근 7일이다.
	 * shortcut: 본 것도 7일 동안 다시 보인다, "본 뒤 새로 생긴 것"이 필요하면 본 시각 칸을 마이그레이션으로 더한다.
	 */
	@GetMapping("/api/me/group-activity")
	List<GroupNewsResponse> activity(@CurrentUser LoginUser user) {
		LocalDate since = LocalDate.now(this.clock).minusDays(DAYS);
		return this.queries.activity(user.id(), since.atStartOfDay(TimeConfig.SEOUL).toOffsetDateTime(), LIMIT);
	}

}
