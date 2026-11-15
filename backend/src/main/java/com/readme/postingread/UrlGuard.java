package com.readme.postingread;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

import com.readme.common.error.ApiException;

/**
 * 서버가 받으러 가도 되는 주소인가(SSRF 막기). 사용자가 준 주소와, 넘어가라는 주소(redirect)마다 부른다.
 *
 * <ul>
 * <li>http·https 만, 호스트가 있어야 하고, 역슬래시와 사용자 정보(user:pass@)가 든 주소는 받지 않는다(구조 문서 5.1).</li>
 * <li>호스트 이름을 풀어 나온 주소가 하나라도 공개 주소가 아니면 받지 않는다: 루프백, 사설망, 링크 로컬(169.254.169.254 포함),
 * 멀티캐스트, 지정 안 됨, IPv6 고유 로컬, 그리고 이런 IPv4 를 품은 IPv6(매핑, 호환, NAT64, 6to4).</li>
 * </ul>
 */
final class UrlGuard {

	private static final String NOT_ALLOWED = "읽을 수 없는 주소입니다. 공고 본문을 복사해 붙여넣어 주세요";

	/** 공개 주소가 아닌 IPv4 대역: {주소, 앞자리 비트 수}. */
	private static final int[][] PRIVATE_V4 = {
			{ ip(0, 0, 0, 0), 8 }, // 이 네트워크(0.x 는 리눅스에서 자기 자신으로 간다)
			{ ip(10, 0, 0, 0), 8 }, // 사설망
			{ ip(100, 64, 0, 0), 10 }, // 통신사 NAT, 클라우드 내부
			{ ip(127, 0, 0, 0), 8 }, // 루프백
			{ ip(169, 254, 0, 0), 16 }, // 링크 로컬, 클라우드 메타데이터
			{ ip(172, 16, 0, 0), 12 }, // 사설망
			{ ip(192, 0, 0, 0), 24 }, // IETF 예약
			{ ip(192, 168, 0, 0), 16 }, // 사설망
			{ ip(198, 18, 0, 0), 15 }, // 장비 시험용
			{ ip(224, 0, 0, 0), 4 }, // 멀티캐스트
			{ ip(240, 0, 0, 0), 4 } }; // 예약, 브로드캐스트

	private UrlGuard() {
	}

	/** http·https 주소로 읽는다. 아니면 422 FETCH_FAILED. */
	static URI parse(String url) {
		if (url == null || url.indexOf('\\') >= 0) {
			throw notAllowed();
		}
		URI uri;
		try {
			uri = new URI(url);
		}
		catch (URISyntaxException ex) {
			throw notAllowed();
		}
		String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw notAllowed();
		}
		if (uri.getHost() == null || uri.getHost().isEmpty() || uri.getRawUserInfo() != null) {
			throw notAllowed();
		}
		return uri;
	}

	/**
	 * 호스트 이름을 풀어 나온 주소가 모두 공개 주소인지 본다. 이름을 풀지 못해도 422 FETCH_FAILED.
	 *
	 * <p>shortcut: PageFetcher 는 보내기 바로 앞에 이것을 부르고, 몇 밀리초 뒤 HttpClient 가 이름을 다시 푼다. 그 조회는 대개 JVM 이
	 * 30초 동안 기억한 답(networkaddress.cache.ttl 기본값), 곧 여기서 검사한 주소를 받는다. 남은 틈: 앞선 조회로 생긴 기억이 바로 그
	 * 몇 밀리초 안에 끝나면 HttpClient 가 새로 묻고, 그때 이름이 사설 주소로 바뀌어 있으면(DNS rebinding) 막지 못한다. 공격자는 이
	 * 순간을 맞히려 되풀이해 볼 수 있다. 검사한 주소로 바로 연결하거나, 나가는 요청을 공개 주소로만 보내는 프록시·방화벽을 두면 닫힌다.
	 * AWS 에서는 인스턴스 메타데이터를 IMDSv2 만(토큰 필수, hop limit 1) 받게 해 GET 만 보내는 이 요청으로는 읽지 못하게 한다.
	 */
	static void requirePublic(URI uri, boolean allowLoopback) {
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(uri.getHost());
		}
		catch (UnknownHostException ex) {
			throw new ApiException(PostingReadErrorCode.FETCH_FAILED, "사이트 주소를 찾지 못했습니다. 주소를 확인하거나 공고 본문을 복사해 붙여넣어 주세요");
		}
		for (InetAddress address : addresses) {
			if (!isPublic(address) && !(allowLoopback && address.isLoopbackAddress())) {
				throw notAllowed();
			}
		}
	}

	static boolean isPublic(InetAddress address) {
		byte[] b = address.getAddress();
		if (b.length == 4) {
			return isPublicV4(b);
		}
		if (allZero(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
			return isPublicV4(Arrays.copyOfRange(b, 12, 16)); // ::ffff:a.b.c.d
		}
		if (allZero(b, 0, 12)) {
			return isPublicV4(Arrays.copyOfRange(b, 12, 16)); // ::a.b.c.d, ::, ::1
		}
		if (b[0] == 0 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b && allZero(b, 4, 12)) {
			return isPublicV4(Arrays.copyOfRange(b, 12, 16)); // NAT64 64:ff9b::a.b.c.d
		}
		if (b[0] == 0x20 && b[1] == 0x02) {
			return isPublicV4(Arrays.copyOfRange(b, 2, 6)); // 6to4 2002:aabb:ccdd::
		}
		int first = b[0] & 0xff;
		int second = b[1] & 0xff;
		boolean uniqueLocal = (first & 0xfe) == 0xfc; // fc00::/7
		boolean multicast = first == 0xff;
		boolean linkOrSiteLocal = first == 0xfe && (second & 0x80) == 0x80; // fe80::/10, fec0::/10
		return !(uniqueLocal || multicast || linkOrSiteLocal);
	}

	private static boolean isPublicV4(byte[] b) {
		int address = ip(b[0] & 0xff, b[1] & 0xff, b[2] & 0xff, b[3] & 0xff);
		for (int[] block : PRIVATE_V4) {
			int mask = -1 << (32 - block[1]);
			if ((address & mask) == (block[0] & mask)) {
				return false;
			}
		}
		return true;
	}

	private static boolean allZero(byte[] b, int from, int to) {
		for (int i = from; i < to; i++) {
			if (b[i] != 0) {
				return false;
			}
		}
		return true;
	}

	private static int ip(int a, int b, int c, int d) {
		return (a << 24) | (b << 16) | (c << 8) | d;
	}

	private static ApiException notAllowed() {
		return new ApiException(PostingReadErrorCode.FETCH_FAILED, NOT_ALLOWED);
	}

}
