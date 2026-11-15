package com.readme.profile;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.profile.ProfileRequests.ProfileRequest;

/** 기본 프로필 API(설계 4절). 내 프로필만 있다. 그룹이나 남에게 보이지 않는다. */
@RestController
class ProfileController {

	private final ProfileQueries queries;

	private final ProfileService service;

	private final Clock clock;

	ProfileController(ProfileQueries queries, ProfileService service, Clock clock) {
		this.queries = queries;
		this.service = service;
		this.clock = clock;
	}

	@GetMapping("/api/me/profile")
	ProfileResponse find(@CurrentUser LoginUser user) {
		return this.queries.find(user.id());
	}

	/** 모든 칸을 보낸 값으로 바꾸고 바뀐 프로필을 돌려준다. */
	@PutMapping("/api/me/profile")
	ProfileResponse update(@Valid @RequestBody ProfileRequest request, @CurrentUser LoginUser user) {
		this.service.update(user.id(), request.toInput(LocalDate.now(this.clock)));
		return this.queries.find(user.id());
	}

}
