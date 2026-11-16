package com.readme.inboundmail;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.readme.inboundmail.SenderCheck.Verdict;

import static org.assertj.core.api.Assertions.assertThat;

/** 보낸 사람 확인과 Authentication-Results 읽기(메일 설계 M3, M6, M7). */
class SenderCheckTests {

	private static final Set<String> MINE = Set.of("name@gmail.com");

	/** Gmail 자동 전달: 봉투가 이름+caf_=…@gmail.com, From 은 원래 보낸 회사 */
	private static final String FORWARDED = "name+caf_=t0k3n=inbound.example@gmail.com";

	@Test
	void gmailAutoForwardIsAcceptedBySpf() {
		AuthResults auth = AuthResults.parse("mx.cloudflare.net; dkim=pass header.d=company.example header.s=s1; "
				+ "spf=pass (mx.cloudflare.net: domain of " + FORWARDED + " designates 209.85.0.1 as permitted sender) "
				+ "smtp.mailfrom=" + FORWARDED + "; dmarc=pass header.from=company.example");

		assertThat(SenderCheck.check(FORWARDED, "recruit@company.example", auth, MINE)).isEqualTo(Verdict.ACCEPT);
		assertThat(SenderCheck.check("<" + FORWARDED.toUpperCase() + ">", "recruit@company.example", auth, MINE))
			.isEqualTo(Verdict.ACCEPT);
	}

	@Test
	void forwardIsRejectedWhenSpfFailsOrTheAddressIsNotRegistered() {
		AuthResults failed = AuthResults.parse("mx.cloudflare.net; spf=fail smtp.mailfrom=" + FORWARDED);
		AuthResults passed = AuthResults.parse("mx.cloudflare.net; spf=pass smtp.mailfrom=" + FORWARDED);

		assertThat(SenderCheck.check(FORWARDED, "recruit@company.example", failed, MINE)).isEqualTo(Verdict.REJECT);
		assertThat(SenderCheck.check(FORWARDED, "recruit@company.example", passed, Set.of("other@gmail.com")))
			.isEqualTo(Verdict.REJECT);
		assertThat(SenderCheck.check(FORWARDED, "recruit@company.example", passed, Set.of())).isEqualTo(Verdict.REJECT);
	}

	@Test
	void spfMustPassForTheEnvelopeDomain() {
		AuthResults auth = AuthResults.parse("mx.cloudflare.net; spf=pass smtp.mailfrom=bounce@relay.example");

		assertThat(SenderCheck.check("name@gmail.com", null, auth, MINE)).isEqualTo(Verdict.REJECT);
	}

	@Test
	void manualForwardIsAcceptedByDkimOrDmarcOfTheFromAddress() {
		AuthResults dkim = AuthResults.parse("mx.cloudflare.net; spf=pass smtp.mailfrom=bounce@relay.example; "
				+ "dkim=pass header.d=\"gmail.com\" header.s=20230601");
		AuthResults dmarc = AuthResults.parse("mx.cloudflare.net; dmarc=pass header.from=gmail.com");
		AuthResults other = AuthResults.parse("mx.cloudflare.net; dkim=pass header.d=relay.example");

		assertThat(SenderCheck.check("bounce@relay.example", "Name@Gmail.com", dkim, MINE)).isEqualTo(Verdict.ACCEPT);
		assertThat(SenderCheck.check(null, "name@gmail.com", dmarc, MINE)).isEqualTo(Verdict.ACCEPT);
		assertThat(SenderCheck.check(null, "name@gmail.com", other, MINE)).isEqualTo(Verdict.REJECT);
	}

	@Test
	void subdomainsAlignButLookalikeDomainsDoNot() {
		Set<String> mine = Set.of("hr@company.example");

		assertThat(SenderCheck.check(null, "hr@company.example",
				AuthResults.parse("mx.cloudflare.net; dkim=pass header.d=mail.company.example"), mine))
			.isEqualTo(Verdict.ACCEPT);
		assertThat(SenderCheck.check(null, "hr@company.example",
				AuthResults.parse("mx.cloudflare.net; dkim=pass header.d=evilcompany.example"), mine))
			.isEqualTo(Verdict.REJECT);
		assertThat(SenderCheck.check(null, "hr@company.example",
				AuthResults.parse("mx.cloudflare.net; dkim=pass header.d=company.example.evil"), mine))
			.isEqualTo(Verdict.REJECT);
	}

	@Test
	void onlyTheFirstHeaderFromCloudflareIsTrusted() {
		String passing = "spf=pass smtp.mailfrom=name@gmail.com";

		assertThat(SenderCheck.check("name@gmail.com", null, AuthResults.parse("mx.google.com; " + passing), MINE))
			.isEqualTo(Verdict.REJECT);
		// 같은 이름의 헤더가 이어 붙었다: 뒤의 것(보낸 쪽이 넣었을 수 있다)은 보지 않는다
		assertThat(SenderCheck.check("name@gmail.com", null,
				AuthResults.parse("mx.cloudflare.net; spf=fail smtp.mailfrom=name@gmail.com, mx.evil.example; " + passing),
				MINE))
			.isEqualTo(Verdict.REJECT);
		assertThat(SenderCheck.check("name@gmail.com", null,
				AuthResults.parse("mx.cloudflare.net; " + passing + ", mx.google.com; dkim=fail header.d=gmail.com"), MINE))
			.isEqualTo(Verdict.ACCEPT);
		assertThat(SenderCheck.check("name@gmail.com", null, AuthResults.parse(null), MINE)).isEqualTo(Verdict.REJECT);
		assertThat(SenderCheck.check("name@gmail.com", null, AuthResults.parse(""), MINE)).isEqualTo(Verdict.REJECT);
	}

	@Test
	void commentsWithSemicolonsAreIgnored() {
		AuthResults auth = AuthResults.parse(
				"mx.cloudflare.net; spf=pass (domain of name@gmail.com; designates (nested) x) smtp.mailfrom=name@gmail.com");

		assertThat(auth.passes("spf", "smtp.mailfrom", "gmail.com")).isTrue();
	}

	@Test
	void gmailForwardingConfirmationIsKeptApart() {
		AuthResults google = AuthResults.parse("mx.cloudflare.net; dkim=pass header.d=google.com; "
				+ "spf=pass smtp.mailfrom=3abc@gaia.bounces.google.com");
		AuthResults none = AuthResults.parse("mx.cloudflare.net; dkim=fail header.d=google.com");

		assertThat(SenderCheck.check("3abc@gaia.bounces.google.com", "forwarding-noreply@google.com", google, Set.of()))
			.isEqualTo(Verdict.GMAIL_FORWARDING);
		assertThat(SenderCheck.check("x@evil.example", "forwarding-noreply@google.com", none, Set.of()))
			.isEqualTo(Verdict.REJECT);
	}

}
