/**
 * 성적증명서 읽기(학점 계산기). 올린 PDF·사진에서 과목 줄(학년, 학기, 이수구분, 과목명, 학점, 성적, 재수강, 확인 필요 칸)을 읽어
 * 돌려준다. 합계와 환산은 화면이 계산한다. 파일과 읽은 결과는 저장하지 않는다(요청 동안 메모리에만 있다, GC2).
 *
 * <p>테이블: 없음(LLM 사용액만 llm 의 llm_usage 에 남는다). API: POST /api/transcripts/read.
 * <p>파일의 글은 extraction 의 UploadedFiles 가 읽고(스캔본·사진은 Claude 로 옮겨 적는다), 그 글을 llm 의 Llm 으로 과목 줄로 바꾼 뒤
 * 이 패키지가 값을 맞춘다(TranscriptReader).
 * 근거: docs/리드미_설계_학점계산기.md 2절, 4절.
 */
package com.readme.transcript;
