package com.readme.keyword;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 태그 입력 후보(경험카드 설계 4.1). 다른 표기를 쳐도 대표 표기를 준다(k8s → Kubernetes, "k8s의 대표 표기").
 */
@RestController
class KeywordController {

	/** 후보는 이만큼까지. */
	static final int LIMIT = 10;

	/** 친 글자가 비었으면 빈 목록. 친 글자와 같은 표기, 그 글자로 시작하는 표기, 그 글자가 든 표기의 순이다. */
	@GetMapping("/api/keywords")
	List<KeywordSuggestion> suggest(@RequestParam(defaultValue = "") String q) {
		return KeywordDictionary.standard().suggest(q, LIMIT);
	}

}
