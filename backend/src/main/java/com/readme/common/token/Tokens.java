package com.readme.common.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 링크에 넣는 토큰(초대, 가입 확인, 비밀번호 재설정).
 *
 * <p>원문은 링크에만 있고 DB 에는 {@link #sha256Hex} 만 둔다(*_tokens.token_hash, group_invites.token_hash, char(64)).
 * DB 가 새도 링크를 만들 수 없다.
 */
public final class Tokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Tokens() {
	}

	/** 추측할 수 없는 토큰. 32바이트를 주소에 그대로 쓸 수 있는 base64url(43자)로 쓴다. */
	public static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** DB 에 두는 값. 토큰 원문의 SHA-256 을 소문자 16진수 64자로. */
	public static String sha256Hex(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
