/**
 * 키워드 사전과 숫자 추출. 태그를 대표 표기로 바꾸고(k8s → Kubernetes), 글에서 사전 낱말과 숫자를 뽑는다.
 *
 * <p>사전 파일은 resources/dictionary 에 있다. API: /api/keywords(태그 입력 후보).
 * <p>다른 기능은 {@link com.readme.keyword.KeywordDictionary} 로 태그를 대표 표기로 바꾸거나 글에서 사전 낱말을 찾고(공고 읽어 오기의 키워드),
 * {@link com.readme.keyword.Numbers} 로 숫자를 뽑아 견준다.
 * jobfit 의 clean/keywords.py(사전 읽기, normalize, expand, scan, find)와 numbers.py(extract, same_value)를 옮겼다(구조 문서 10절).
 */
package com.readme.keyword;
