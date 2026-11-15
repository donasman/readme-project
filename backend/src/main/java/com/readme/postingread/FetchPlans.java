package com.readme.postingread;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.readme.posting.PostingKey;
import com.readme.posting.PostingKeys;

/**
 * 사이트별 수집 규칙: 본문이 공고 주소가 아닌 곳에 있는 사이트를 안다. jobfit 의 src/jobfit/fetch/sites.py(plan_for)와
 * fetcher.py 의 같은 호스트 iframe 고르기를 옮겼다.
 *
 * <p>jobfit 실측(2026-10):
 * <ul>
 * <li>사람인: relay/view 주소는 메뉴 글자만 온다. 요약은 jobs/view?rec_idx=N, 상세요강은 jobs/relay/view-detail?rec_idx=N&amp;rec_seq=0.</li>
 * <li>잡코리아: 공고 주소에는 메뉴·요약뿐. 상세요강은 GI_Read_Comt_Ifrm?Gno=N(iframe).</li>
 * <li>원티드: 화면을 스크립트로 그려 본문이 비어 올 수 있다.</li>
 * <li>그 밖: 받은 HTML 에서 같은 호스트 iframe 을 하나까지 더 받는다.</li>
 * </ul>
 * 상세요강은 같은 공고의 본문 페이지다. 다른 링크는 따라가지 않는다. 새 사이트는 여기에 규칙 하나를 더한다.
 */
final class FetchPlans {

	private FetchPlans() {
	}

	/**
	 * 한 공고를 받는 계획.
	 *
	 * @param mainUrl       요약·제목이 있는 페이지
	 * @param detailUrls    상세요강 페이지
	 * @param referer       보낼 Referer. 없으면 ""
	 * @param followIframes 모르는 사이트만. 같은 호스트 iframe 하나까지
	 */
	record Plan(String mainUrl, List<String> detailUrls, String referer, boolean followIframes) {
	}

	static Plan plan(String url) {
		PostingKey key = PostingKeys.derive(url, null, null, null);
		String n = key.number();
		if ("saramin".equals(key.site()) && !n.isEmpty()) {
			return new Plan("https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=" + n,
					List.of("https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?rec_idx=" + n + "&rec_seq=0"), url, false);
		}
		if ("jobkorea".equals(key.site()) && !n.isEmpty()) {
			return new Plan("https://www.jobkorea.co.kr/Recruit/GI_Read/" + n,
					List.of("https://www.jobkorea.co.kr/Recruit/GI_Read_Comt_Ifrm?Gno=" + n), url, false);
		}
		if ("wanted".equals(key.site())) {
			return new Plan(url, List.of(), "", false);
		}
		return new Plan(url, List.of(), "", true);
	}

	/** 받은 페이지와 같은 호스트로 가는 첫 iframe 의 주소. about: 과 다른 호스트는 건너뛴다. */
	static Optional<String> sameHostIframe(Document page, URI base) {
		String host = authority(base);
		for (Element frame : page.select("iframe")) {
			String src = frame.attr("src").isEmpty() ? frame.attr("data-src") : frame.attr("src");
			if (src.isEmpty() || src.startsWith("about:")) {
				continue;
			}
			try {
				URI full = base.resolve(src.strip());
				if (Objects.equals(authority(full), host)) {
					return Optional.of(full.toString());
				}
			}
			catch (IllegalArgumentException ex) {
				// 주소로 읽을 수 없는 src
			}
		}
		return Optional.empty();
	}

	private static String authority(URI uri) {
		return (uri.getRawAuthority() != null) ? uri.getRawAuthority().toLowerCase(Locale.ROOT) : null;
	}

}
