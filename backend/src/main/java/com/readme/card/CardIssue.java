package com.readme.card;

/**
 * 카드 검사 결과 한 줄. 프런트의 CardIssue 모양이다.
 *
 * @param level   "오류" 또는 "경고"
 * @param field   걸린 칸의 화면 이름("요약", "근거 숫자" 등)
 * @param message 화면에 그대로 보이는 글
 */
record CardIssue(String level, String field, String message) {
}
