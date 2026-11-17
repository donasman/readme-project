package com.readme.inboundmail;

import java.util.Locale;
import java.util.Set;

/**
 * 받은 메일의 보낸 사람 확인(메일 설계 M6, M7). Spring 없이 돈다.
 *
 * <ul>
 * <li>봉투 From(+ 뒤를 뗀 주소)이 보내는 주소 목록에 있고, 그 도메인으로 spf=pass 면 받는다. Gmail 자동 전달은 봉투가
 * {@code 이름+caf_=…@gmail.com} 이다.</li>
 * <li>From 헤더 주소가 목록에 있고, 그 도메인으로 dkim=pass(header.d)나 dmarc=pass(header.from)면 받는다. 손으로 전달한 메일이다.</li>
 * <li>Gmail 의 전달 확인 메일(forwarding-noreply@google.com, google.com 으로 dkim·spf pass)은 목록과 무관하게 따로 둔다.</li>
 * </ul>
 */
final class SenderCheck {

	static final String GMAIL_FORWARDING = "forwarding-noreply@google.com";

	enum Verdict {

		/** 등록한 보낸 사람이다. 분류한다 */
		ACCEPT,

		/** Gmail 전달 확인 메일이다. 확인 코드만 둔다 */
		GMAIL_FORWARDING,

		/** 받지 않는다 */
		REJECT

	}

	private SenderCheck() {
	}

	/**
	 * @param envelopeFrom 봉투 MAIL FROM. 없으면 null
	 * @param headerFrom   From 헤더의 주소. 없으면 null
	 * @param registered   보내는 주소 목록(소문자)
	 */
	static Verdict check(String envelopeFrom, String headerFrom, AuthResults auth, Set<String> registered) {
		String from = normalize(headerFrom);
		if (GMAIL_FORWARDING.equals(from)
				&& (auth.passes("dkim", "header.d", "google.com") || auth.passes("spf", "smtp.mailfrom", "google.com"))) {
			return Verdict.GMAIL_FORWARDING;
		}
		String envelope = withoutTag(normalize(envelopeFrom));
		if (envelope != null && registered.contains(envelope)
				&& auth.passes("spf", "smtp.mailfrom", AuthResults.domainOf(envelope))) {
			return Verdict.ACCEPT;
		}
		if (from != null && (registered.contains(from) || registered.contains(withoutTag(from)))) {
			String domain = AuthResults.domainOf(from);
			if (auth.passes("dkim", "header.d", domain) || auth.passes("dmarc", "header.from", domain)) {
				return Verdict.ACCEPT;
			}
		}
		return Verdict.REJECT;
	}

	/** 소문자로, 앞뒤의 꺾쇠와 빈칸을 뗀다. 비었거나 @ 가 없으면 null. */
	static String normalize(String address) {
		if (address == null) {
			return null;
		}
		String value = address.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith("<") && value.endsWith(">")) {
			value = value.substring(1, value.length() - 1).trim();
		}
		return (value.indexOf('@') > 0) ? value : null;
	}

	/** {@code 이름+꼬리@도메인} → {@code 이름@도메인}. */
	private static String withoutTag(String address) {
		if (address == null) {
			return null;
		}
		int at = address.lastIndexOf('@');
		int plus = address.indexOf('+');
		return (plus > 0 && plus < at) ? address.substring(0, plus) + address.substring(at) : address;
	}

}
