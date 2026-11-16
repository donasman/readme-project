package com.readme.inboundmail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 받는 API 의 서명(메일 설계 M1, M2). */
class MailSignatureTests {

	private static final String SECRET = "secret";

	private static final Instant NOW = Instant.parse("2026-11-12T15:30:00Z");

	private static final String TS = String.valueOf(NOW.getEpochSecond());

	private static final byte[] RAW = "Subject: 안녕\r\n\r\n본문\r\n".getBytes(StandardCharsets.UTF_8);

	private static final String AUTH = "mx.cloudflare.net; spf=pass smtp.mailfrom=a@b.example";

	@Test
	void signedRequestIsAccepted() {
		String signature = MailSignature.sign(SECRET, TS, "a@b.example", "t@in.example", AUTH, RAW);

		assertThat(signature).matches("[0-9a-f]{64}");
		assertThat(verify(TS, AUTH, RAW, signature, NOW)).isTrue();
		assertThat(verify(TS, AUTH, RAW, signature.toUpperCase(), NOW)).isTrue();
	}

	@Test
	void anyChangedPartBreaksTheSignature() {
		String signature = MailSignature.sign(SECRET, TS, "a@b.example", "t@in.example", AUTH, RAW);

		assertThat(verify(TS, AUTH.replace("pass", "fail"), RAW, signature, NOW)).isFalse();
		assertThat(verify(TS, AUTH, "Subject: x\r\n\r\n".getBytes(StandardCharsets.UTF_8), signature, NOW)).isFalse();
		assertThat(MailSignature.verify(SECRET, TS, "c@b.example", "t@in.example", AUTH, RAW, signature, NOW)).isFalse();
		assertThat(MailSignature.verify("other", TS, "a@b.example", "t@in.example", AUTH, RAW, signature, NOW)).isFalse();
	}

	@Test
	void timestampMustBeWithinFiveMinutes() {
		for (long skew : new long[] { -300, 300 }) {
			String ts = String.valueOf(NOW.getEpochSecond() + skew);
			assertThat(verify(ts, AUTH, RAW, MailSignature.sign(SECRET, ts, "a@b.example", "t@in.example", AUTH, RAW), NOW))
				.isTrue();
		}
		for (long skew : new long[] { -301, 301 }) {
			String ts = String.valueOf(NOW.getEpochSecond() + skew);
			assertThat(verify(ts, AUTH, RAW, MailSignature.sign(SECRET, ts, "a@b.example", "t@in.example", AUTH, RAW), NOW))
				.isFalse();
		}
	}

	@Test
	void missingSecretOrHeadersAreRejected() {
		String signature = MailSignature.sign(SECRET, TS, "a@b.example", "t@in.example", AUTH, RAW);

		assertThat(MailSignature.verify("", TS, "a@b.example", "t@in.example", AUTH, RAW, signature, NOW)).isFalse();
		assertThat(MailSignature.verify(null, TS, "a@b.example", "t@in.example", AUTH, RAW, signature, NOW)).isFalse();
		assertThat(verify(null, AUTH, RAW, signature, NOW)).isFalse();
		assertThat(verify("soon", AUTH, RAW, signature, NOW)).isFalse();
		assertThat(verify(TS, AUTH, RAW, null, NOW)).isFalse();
	}

	@Test
	void missingHeadersAreSignedAsEmpty() {
		String signature = MailSignature.sign(SECRET, TS, null, "t@in.example", null, RAW);

		assertThat(MailSignature.verify(SECRET, TS, "", "t@in.example", "", RAW, signature, NOW)).isTrue();
	}

	private static boolean verify(String ts, String auth, byte[] raw, String signature, Instant now) {
		return MailSignature.verify(SECRET, ts, "a@b.example", "t@in.example", auth, raw, signature, now);
	}

}
