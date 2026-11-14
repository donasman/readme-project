package com.readme.card;

/**
 * GET /api/cards/tags 의 한 항목: 내 카드에 쓰인 태그와 그 태그가 붙은 카드 수. 프런트의 CardTag 모양이다.
 */
record CardTagResponse(String name, long count) {
}
