package com.readme.profile;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.auth.GoogleValues;
import com.readme.auth.SignupValues;

/**
 * 다른 기능이 부르는 프로필 쓰기. 가입 값(남은 작업 4단계)과 자료에서 뽑은 값(5단계)이 쓴다.
 *
 * <p>가입 값은 auth 가 내는 이벤트를 같은 트랜잭션에서 받는다(auth 는 profile 을 불러오지 않는다). 이메일 가입의 값과 구글이 인증 전
 * 계정을 차지한 것은 {@link SignupValues}, 구글 로그인·연결 뒤 People API 로 읽은 값은 {@link GoogleValues} 다.
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

	/**
	 * 인증 전 계정의 가입 값. 그 계정은 로그인한 적이 없어 앞서 같은 이메일로 가입한 사람(남일 수 있다)의 값 말고는 프로필에 든 것이 없다.
	 * 그래서 빈 칸만 채우지 않고 프로필을 이 값(이름과 목록은 비운다)으로 바꾼다. 값이 null 이면 지운다.
	 */
	@EventListener
	@Transactional
	void replaceWithSignupValues(SignupValues event) {
		this.profiles.insertIfAbsent(event.userId());
		this.profiles.findById(event.userId())
			.orElseThrow()
			.replace(ProfileDraft.basics(null, event.birthDate(), event.gender()).cleaned(LocalDate.now(this.clock)),
					this.clock.instant());
	}

	/** 구글이 준 값. 빈 칸만 채운다(가입 값이나 사용자가 고친 값을 덮어쓰지 않는다). 이름은 넣지 않는다(표시 이름과 따로다). */
	@EventListener
	@Transactional
	void fillWithGoogleValues(GoogleValues event) {
		fillBlanks(event.userId(), ProfileDraft.basics(null, event.birthDate(), event.gender()));
	}

}
