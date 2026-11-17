package com.readme.profile;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.profile.Profile.ProfileInput;

@Service
class ProfileService {

	private final ProfileRepository profiles;

	private final Clock clock;

	ProfileService(ProfileRepository profiles, Clock clock) {
		this.profiles = profiles;
		this.clock = clock;
	}

	/** 내 프로필의 모든 칸을 바꾼다. 처음이면 만든다. */
	@Transactional
	void update(long userId, ProfileInput input) {
		this.profiles.insertIfAbsent(userId);
		this.profiles.findById(userId).orElseThrow().replace(input, this.clock.instant());
	}

}
