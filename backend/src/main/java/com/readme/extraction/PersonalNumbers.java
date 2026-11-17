package com.readme.extraction;

import java.util.regex.Pattern;

/**
 * 자료 본문에서 주민등록번호와 전화번호 꼴을 가린다. 둘 다 받지 않는 값인데 이력서에는 자주 있다(프로필 방향 문서 3절 5번).
 * 자료 올리기만 쓴다. 공고 읽어 오기의 글은 가리지 않는다(공고의 연락처는 회사의 것이고 사용자가 그 글을 본다).
 *
 * <p>날짜(2025-03-01, 2023.03 ~ 2024.02), 기간, 금액, 비율, 버전이 걸리지 않게 앞자리를 좁혔다. 경계는 PersonalNumbersTests 에 있다.
 */
final class PersonalNumbers {

	static final String RRN_MARK = "[주민번호]";

	static final String PHONE_MARK = "[전화번호]";

	/** 하이픈, Word·한글이 바꿔 넣는 대시(‐ ‑ ‒ – — ―), 빼기 기호, 전각 하이픈 */
	private static final String DASH = "[-\\u2010-\\u2015\\u2212\\uFF0D]";

	/**
	 * 생년월일 6자리(달 01~12, 날 01~31), 대시(앞뒤 공백은 된다)나 붙여 씀, 뒷자리 7자리(첫 자리는 성별·세기의 1~8. 외국인등록번호가
	 * 5~8이다). 앞뒤에 숫자가 더 붙으면 다른 번호다.
	 */
	private static final Pattern RRN = Pattern.compile("(?<!\\d)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])"
			+ "(?:\\h*" + DASH + "\\h*)?[1-8]\\d{6}(?!\\d)");

	/** 번호 사이: 대시나 점(앞뒤 공백은 된다), 공백, 또는 붙여 씀 */
	private static final String SEP = "(?:\\h*(?:" + DASH + "|\\.)\\h*|\\h+)?";

	/** 앞 0을 뗀 국번: 휴대폰 10·11·16~19, 서울 2, 지역 31~64, 인터넷 전화 70, 평생번호 50X */
	private static final String PREFIX = "(?:1[016-9]|2|[3-6][1-5]|70|50\\d)";

	/**
	 * 0으로 시작하는 국번(괄호로 싸도 된다) 또는 +82(뒤의 0이나 (0)은 있어도 된다), 가운데 3~4자리, 끝 4자리. 1588 같은 대표번호는
	 * 회사의 번호이고 4자리-4자리가 범위(1500-2000명)와 겹쳐 가리지 않는다.
	 */
	private static final Pattern PHONE = Pattern.compile("(?:\\+\\h*82" + SEP + "(?:\\(0\\)\\h*|0)?" + PREFIX
			+ "|(?<!\\d)(?:\\(0" + PREFIX + "\\)|0" + PREFIX + "))" + SEP + "\\d{3,4}" + SEP + "\\d{4}(?!\\d)");

	private PersonalNumbers() {
	}

	/** 주민번호 꼴은 [주민번호]로, 전화번호 꼴은 [전화번호]로 바꾼다. 둘 다 원래보다 짧아 100만 자 상한을 다시 볼 일이 없다. */
	static String mask(String text) {
		// shortcut: 한글로 쓴 번호(공일공), 자리마다 띄어 쓴 번호, + 없이 쓴 82-10-…, 공백으로만 나눈 주민번호는 못 가린다, 새는 사례가 보이면 꼴을 더한다
		return PHONE.matcher(RRN.matcher(text).replaceAll(RRN_MARK)).replaceAll(PHONE_MARK);
	}

}
