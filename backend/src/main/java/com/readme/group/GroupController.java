package com.readme.group;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;

@RestController
class GroupController {

	private final GroupQueries queries;

	private final Clock clock;

	GroupController(GroupQueries queries, Clock clock) {
		this.queries = queries;
		this.clock = clock;
	}

	/** 내가 속한 그룹들. 들어간 순서다. 프런트의 첫 화면은 첫 그룹으로 간다. */
	@GetMapping("/api/groups")
	List<GroupResponse> myGroups(@CurrentUser LoginUser user) {
		return this.queries.findMyGroups(user.id(), LocalDate.now(this.clock));
	}

}
