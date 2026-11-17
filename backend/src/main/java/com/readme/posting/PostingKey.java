package com.readme.posting;

/**
 * 공고키 하나.
 *
 * @param key    postings.posting_key 에 넣는 값. 예: "saramin:10000001", "url:example.com/careers/42?page=2", "local:779642d2"
 * @param site   saramin · jobkorea · wanted · jumpit · remember · programmers · toss · url · local
 * @param number 사이트 공고번호. 없으면 ""
 */
public record PostingKey(String key, String site, String number) {

	public boolean hasUrl() {
		return !"local".equals(this.site);
	}

}
