/**
 * 공고 읽어 오기. 링크나 붙여넣은 본문에서 공고를 올릴 때 넣는 칸(회사명, 공고명, 마감 …)을 규칙으로 읽고, 칸마다 근거와 읽지 못한
 * 칸, 원문 글자, 공고 내용 묶음을 돌려준다. 읽은 결과는 서버에 두지 않는다.
 *
 * <p>테이블: 없음(같은 공고 안내를 위해 postings, group_posts 를 읽기만 한다). API: POST /api/postings/read.
 * <p>jobfit 에서 옮긴 것: html2text.py(HtmlText), sections.py(PostingSections), dates.py(Deadlines), meta.py(PostingMeta),
 * sites.py(FetchPlans), robots.py 와 urllib.robotparser(RobotsTxt), fetcher.py 의 받기·인코딩(PageFetcher).
 * <p>멤버 확인은 group 의 GroupAccess, 같은 공고는 posting 의 PostingKeys 로 알아본다.
 * 근거: docs/리드미_설계_공고.md 4.6, 10절.
 */
package com.readme.postingread;
