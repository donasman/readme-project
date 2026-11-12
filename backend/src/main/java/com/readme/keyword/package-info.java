/**
 * 키워드 사전과 숫자 추출. 태그를 대표 표기로 바꾸고(k8s → Kubernetes), 글에서 숫자를 뽑아 비교한다.
 *
 * <p>사전 파일은 resources/dictionary 에 있다. API: /api/keywords.
 * <p>jobfit 의 clean/keywords.py 와 numbers.py(extract, same_value)를 옮겨 온다. 테스트를 먼저 옮긴다.
 */
package com.readme.keyword;
