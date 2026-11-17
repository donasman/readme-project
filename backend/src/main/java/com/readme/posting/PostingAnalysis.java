package com.readme.posting;

import java.util.List;

/**
 * 공고 분석(LLM, 공고 설계 4.7). 공고 본체(postings.analysis)에 한 번 두고 같은 공고를 올린 그룹과 내 공고가 함께 본다.
 * 그룹 공고 상세와 내 공고 상세의 analysis 다. 저장하는 jsonb 도 이 모양이다.
 *
 * @param must      필수 역량. 6개까지
 * @param nice      우대 역량. 6개까지
 * @param duties    주요 업무. 5개까지
 * @param keywords  기술·역량 키워드. 사전에 있으면 대표 표기다. 15개까지
 * @param questions 자기소개서 문항. 공고 글에 그대로 있는 것만
 */
public record PostingAnalysis(List<String> must, List<String> nice, List<String> duties, List<String> keywords,
		List<Question> questions) {

	/** @param limit 글자 수. 공고 글에서 그 문항 가까이에 숫자로 있을 때만, 아니면 null */
	public record Question(String text, Integer limit) {
	}

}
