package com.readme.profile;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 기능이 부르는 프로필 쓰기. 가입 값(남은 작업 4단계)과 자료에서 뽑은 값(5단계)이 쓴다.
 */
@Service
public class Profiles {

	private final ProfileRepository profiles;

	private final Clock clock;

	Profiles(ProfileRepository profiles, Clock clock) {
		this.profiles = profiles;
		this.clock = clock;
	}

	/**
	 * 빈 칸만 채운다(프로필 방향 2절 "자료를 여러 번 올렸을 때", 3절 4번). 이름·생년월일·성별은 비어 있을 때만, 학력·자격증·어학·경력은
	 * 그 목록에 한 줄도 없을 때만 넣는다. 이미 있는 값은 건드리지 않는다. 프로필이 없으면 만든다. 틀린 값은 버린다(ProfileDraft.cleaned).
	 */
	@Transactional
	public void fillBlanks(long userId, ProfileDraft draft) {
		this.profiles.insertIfAbsent(userId);
		this.profiles.findById(userId)
			.orElseThrow()
			.fillBlanks(draft.cleaned(LocalDate.now(this.clock)), this.clock.instant());
	}

}
