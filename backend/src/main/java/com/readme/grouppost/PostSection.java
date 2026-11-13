package com.readme.grouppost;

import java.util.List;

/**
 * 공고 내용의 한 묶음(주요 업무, 필수 요건 …). postings.sections(jsonb)의 한 항목이다.
 */
record PostSection(String title, List<String> items) {
}
