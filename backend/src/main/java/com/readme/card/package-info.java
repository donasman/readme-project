/**
 * 경험카드. 만들기·고치기·지우기·확인, 목록 필터, 카드 검사(필수 항목, 기간 형식, 요약 길이, STAR 에만 있는 숫자).
 *
 * <p>테이블: experience_cards, card_tags, card_usable_for, card_facts. API: /api/cards/**.
 * <p>카드 규칙({@code CardRules})은 jobfit 의 experiences.py(check_cards, normalize_type, loose_star_numbers, 기간 정규식)와
 * ai/exp_extract.py 의 ground_star(확인할 때 STAR 의 숫자를 근거 숫자로)를 옮겼다(구조 문서 10절).
 * 태그와 숫자는 keyword 의 KeywordDictionary, Numbers 로 다룬다. 근거: docs/리드미_설계_경험카드.md.
 */
package com.readme.card;
