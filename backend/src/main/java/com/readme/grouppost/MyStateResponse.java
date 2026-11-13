package com.readme.grouppost;

/**
 * 보는 사람(나)의 이 공고 상태. 프런트의 MyState 모양이다.
 *
 * @param interested 이 그룹에서 관심을 켰는지
 * @param status     내 지원 상태 글자(설계 5.3). 내 값이라 공개 범위와 무관하게 준다. 지원함이 아니면 null
 */
record MyStateResponse(boolean interested, String status) {
}
