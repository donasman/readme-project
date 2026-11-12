/**
 * 자료(이력서·포트폴리오) 올리기와 카드 추출 작업.
 *
 * <p>테이블: card_sources, card_extraction_jobs. API: /api/card-sources/**, /api/card-extractions/**.
 * <p>jobfit 의 ai/exp_extract.py(_split, normalize_cards, ground_star, fit_existing)를 옮겨 온다.
 * 프롬프트는 resources/prompts/exp-extract.md.
 */
package com.readme.extraction;
