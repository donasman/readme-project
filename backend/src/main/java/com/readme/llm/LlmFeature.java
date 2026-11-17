package com.readme.llm;

import com.anthropic.models.beta.messages.BetaOutputConfig.Effort;

/**
 * LLM 을 쓰는 기능. llm_usage.feature 에 상수 이름이 들어간다.
 *
 * <p>생각 깊이(effort)는 정리 문서 7절 "사용할 모델"대로다. Claude Opus 5.5 는 생각을 끌 수 없어 effort 로만 정한다.
 * maxTokens 는 생각과 답을 합친 출력 상한이다. 처음 값이고, 각 기능을 만들 때 실제 응답을 보고 맞춘다.
 * 상한까지 쓰면 출력 단가(Opus 5.5 는 백만 토큰당 20달러)로 한 번에 한 달 한도의 큰 몫을 쓰므로 넉넉히만 잡는다.
 */
public enum LlmFeature {

	/** 자료(이력서·포트폴리오)에서 경험카드와 프로필 칸 뽑기. 5,000자 조각마다 한 번(남은 작업 5단계) */
	CARD_EXTRACT(Effort.MEDIUM, 32_000),

	/** 공고 분석: 필수·우대·업무·키워드·문항. 공고마다 한 번, 그룹이 나눠 쓴다(6-2) */
	POSTING_ANALYSIS(Effort.MEDIUM, 16_000),

	/** 공고 읽어 오기에서 규칙으로 못 읽은 칸 채우기(6-1) */
	POSTING_FIELDS(Effort.LOW, 8_000),

	/** 받은 메일 분류(9-3) */
	MAIL_CLASSIFY(Effort.LOW, 8_000),

	/** 글자 층이 없는 PDF(스캔본)와 사진의 글 옮겨 적기(5-8, 6-1). 옮겨 적는 글이 길어 출력 상한이 크다 */
	DOCUMENT_READ(Effort.LOW, 32_000);

	private final Effort effort;

	private final long maxTokens;

	LlmFeature(Effort effort, long maxTokens) {
		this.effort = effort;
		this.maxTokens = maxTokens;
	}

	Effort effort() {
		return this.effort;
	}

	long maxTokens() {
		return this.maxTokens;
	}

}
