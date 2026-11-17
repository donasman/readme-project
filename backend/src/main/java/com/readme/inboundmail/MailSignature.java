package com.readme.inboundmail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Worker 가 받는 API 에 붙이는 서명(메일 설계 M1, M2). Spring 없이 돈다.
 *
 * <p>서명할 바이트: {@code 시각\n봉투 From\n봉투 To\nAuthentication-Results\n} 뒤에 메일 원문. HMAC-SHA256, 소문자 16진수.
 */
final class MailSignature {

	/** Worker 와 서버의 시각이 이만큼까지 달라도 받는다. 가로챈 요청을 그 뒤에 다시 보내지 못한다. */
	static final Duration MAX_SKEW = Duration.ofMinutes(5);

	private MailSignature() {
	}

	/** 서명이 맞고 시각이 MAX_SKEW 안이면 true. 비밀 값이 비어 있거나 헤더가 빠졌으면 false. */
	static boolean verify(String secret, String timestamp, String from, String to, String auth, byte[] raw,
			String signature, Instant now) {
		if (secret == null || secret.isBlank() || timestamp == null || signature == null) {
			return false;
		}
		long seconds;
		try {
			seconds = Long.parseLong(timestamp);
		}
		catch (NumberFormatException ex) {
			return false;
		}
		if (Math.abs(now.getEpochSecond() - seconds) > MAX_SKEW.toSeconds()) {
			return false;
		}
		byte[] expected = sign(secret, timestamp, from, to, auth, raw).getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(expected, signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
	}

	/** 서명 값. 빠진 헤더(null)는 빈 글자로 친다. */
	static String sign(String secret, String timestamp, String from, String to, String auth, byte[] raw) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String head = String.join("\n", orEmpty(timestamp), orEmpty(from), orEmpty(to), orEmpty(auth)) + "\n";
			mac.update(head.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(mac.doFinal(raw));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String orEmpty(String value) {
		return (value != null) ? value : "";
	}

}
