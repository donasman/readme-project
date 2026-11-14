/**
 * 자료(이력서·포트폴리오) 올리기와 카드 추출 작업.
 *
 * <p>테이블: card_sources, card_extraction_jobs. API: /api/card-sources/**, /api/card-extractions/**.
 * <p>지금 있는 것: 자료 올리기·목록·지우기(CardSourceController). 파일은 SourceFiles 가 형식을 알아내 읽고
 * (PdfText, HwpText, ZipXmlText. PDF·HWP 는 IsolatedReader 가 다른 프로세스에서), SourceText 가 다듬고,
 * SourceParts 가 5,000자 조각으로 센다. 원본 파일은 두지 않는다.
 * <p>뒤에 붙일 것: 카드 추출(POST /api/card-sources/{id}/extract, GET /api/card-extractions/{jobId}).
 * jobfit 의 ai/exp_extract.py(_split 은 옮겼다. normalize_cards, ground_star, fit_existing 은 아직)를 옮겨 온다.
 * 프롬프트는 resources/prompts/exp-extract.md.
 * <p>근거: docs/리드미_설계_경험카드.md 4.2.
 */
package com.readme.extraction;
