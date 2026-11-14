package com.readme.keyword;

/**
 * GET /api/keywords 의 한 항목. 프런트의 KeywordSuggestion 모양이다.
 *
 * @param label 대표 표기. 태그로 저장되는 값
 * @param note  다른 표기에 걸렸을 때 그 표기를 적은 글("k8s의 대표 표기"). 대표 표기에 걸렸으면 null
 */
record KeywordSuggestion(String label, String note) {
}
