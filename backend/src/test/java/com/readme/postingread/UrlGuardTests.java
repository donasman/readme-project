package com.readme.postingread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.readme.common.error.ApiException;

/**
 * 서버가 받으러 가도 되는 주소인가(SSRF 막기). 이름을 풀지 않는 IP 주소만 써서 네트워크 없이 돈다.
 */
class UrlGuardTests {

	@ParameterizedTest
	@ValueSource(strings = { "127.0.0.1", "127.255.0.9", "10.0.0.1", "172.16.5.4", "172.31.255.255", "192.168.1.1",
			"169.254.169.254", "100.64.0.1", "0.0.0.0", "0.1.2.3", "224.0.0.1", "239.255.255.250", "255.255.255.255",
			"240.0.0.1", "192.0.0.8", "198.18.0.1",
			// IPv6: 루프백, 지정 안 됨, 링크 로컬, 사이트 로컬, 고유 로컬, 멀티캐스트
			"::1", "::", "fe80::1", "fec0::1", "fc00::1", "fd12:3456::1", "ff02::1",
			// IPv4 를 품은 IPv6: 매핑, 호환, NAT64, 6to4 가 사설·메타데이터 주소를 가리킨다
			"::ffff:127.0.0.1", "::ffff:169.254.169.254", "::10.0.0.1", "64:ff9b::a9fe:a9fe", "2002:a9fe:a9fe::1" })
	void privateAndSpecialAddressesAreNotPublic(String ip) throws UnknownHostException {
		assertThat(UrlGuard.isPublic(InetAddress.getByName(ip))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "8.8.8.8", "1.1.1.1", "172.32.0.1", "172.15.255.255", "100.128.0.1", "192.169.0.1",
			"2606:4700:4700::1111", "2001:4860:4860::8888", "64:ff9b::808:808", "2002:808:808::1" })
	void publicAddressesArePublic(String ip) throws UnknownHostException {
		assertThat(UrlGuard.isPublic(InetAddress.getByName(ip))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ftp://example.com/a", "file:///etc/passwd", "javascript:alert(1)", "gopher://example.com/",
			"https://example.com\\@evil.example/", "https://user:pass@example.com/", "https:///nohost", "//example.com/a",
			"https://exa mple.com/", "data:text/html,hi" })
	void onlyPlainHttpAndHttpsUrls(String url) {
		assertThatThrownBy(() -> UrlGuard.parse(url)).isInstanceOf(ApiException.class)
			.extracting((ex) -> ((ApiException) ex).errorCode())
			.isEqualTo(PostingReadErrorCode.FETCH_FAILED);
	}

	@Test
	void httpAndHttpsAreParsed() {
		assertThat(UrlGuard.parse("https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=1"))
			.isEqualTo(URI.create("https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=1"));
		assertThat(UrlGuard.parse("HTTP://Example.com:8080/a").getHost()).isEqualTo("Example.com");
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://127.0.0.1/", "http://10.1.2.3/", "http://169.254.169.254/latest/meta-data/",
			"http://[::1]/", "http://[::ffff:127.0.0.1]/", "http://[fd00::1]/", "http://0.0.0.0/", "http://localhost/" })
	void hostsThatResolveToPrivateAddressesAreRefused(String url) {
		assertThatThrownBy(() -> UrlGuard.requirePublic(UrlGuard.parse(url), false)).isInstanceOf(ApiException.class)
			.hasMessageContaining("읽을 수 없는 주소");
	}

	@Test
	void loopbackOnlyWhenTestsAllowIt() {
		URI loopback = UrlGuard.parse("http://127.0.0.1:8080/");
		UrlGuard.requirePublic(loopback, true);
		// 테스트 스위치를 켜도 사설망과 메타데이터 주소는 막힌다
		assertThatThrownBy(() -> UrlGuard.requirePublic(UrlGuard.parse("http://169.254.169.254/"), true))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> UrlGuard.requirePublic(UrlGuard.parse("http://10.0.0.1/"), true))
			.isInstanceOf(ApiException.class);
	}

}
