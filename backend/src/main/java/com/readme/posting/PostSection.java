package com.readme.posting;

import java.util.List;

/**
 * 공고 내용의 한 묶음(주요 업무, 필수 요건 …). postings.sections(jsonb)의 한 항목이다. 그룹 공고와 내 공고가 함께 쓴다.
 */
public record PostSection(String title, List<String> items) {
}
