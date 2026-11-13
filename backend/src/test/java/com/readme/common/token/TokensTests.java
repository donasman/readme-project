package com.readme.common.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokensTests {

	@Test
	void tokensAreUrlSafeAndDifferentEachTime() {
		String a = Tokens.newToken();
		String b = Tokens.newToken();

		assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void hashIsLowercaseSha256Hex() {
		// SHA-256("abc") 는 FIPS 180-2 의 시험값이다
		assertThat(Tokens.sha256Hex("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
			.hasSize(64);
	}

}
