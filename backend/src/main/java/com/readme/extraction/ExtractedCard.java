package com.readme.extraction;

import java.util.List;

/**
 * LLM 이 자료 조각에서 뽑은 카드 한 장(구조화 출력의 모양). 칸 이름은 jobfit 의 exp-extract 답과 같다. 모든 칸이 늘 오고, 자료에
 * 없으면 "" 나 [] 다. 다듬기(대표 표기, 문항 유형, 원문에 없는 숫자)는 저장하는 card 패키지가 한다.
 *
 * @param experience 이 장면이 속한 프로젝트·업무의 이름. 같은 경험의 카드는 같은 이름이다(설계 4.8)
 * @param usableFor  지원동기 · 직무역량 · 협업 · 문제해결 · 성장 · 기타 가운데 그 장면으로 답할 수 있는 문항 유형
 */
public record ExtractedCard(String experience, String title, String period, String org, String role, List<String> tags, String summary,
		StarText star, List<String> facts, List<String> usableFor) {

	/** STAR 와 어려움, 배운 점. 어려움과 배운 점은 자료에 적혀 있을 때만 있고 아니면 "" 다. */
	public record StarText(String situation, String task, String action, String result, String obstacle, String learning) {
	}

}
