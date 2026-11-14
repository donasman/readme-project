package com.readme.postingread;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.readme.postingread.FetchPlans.Plan;

/**
 * 사이트별 수집 규칙. jobfit 의 src/jobfit/fetch/sites.py(plan_for)에는 pytest 가 없어서, sites.py 를 python -I 로 직접 돌려
 * 얻은 값을 넣었다. 같은 호스트 iframe 고르기는 jobfit 의 fetcher.py(_same_host_iframes)다.
 */
class FetchPlansTests {

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			https://www.saramin.co.kr/zf_user/jobs/relay/view?view_type=list&rec_idx=10000001 | https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=10000001 | https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?rec_idx=10000001&rec_seq=0 | false
			https://m.saramin.co.kr/zf_user/jobs/view?rec_idx=7 | https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=7 | https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?rec_idx=7&rec_seq=0 | false
			https://www.jobkorea.co.kr/Recruit/GI_Read/10000002?Oem_Code=C1 | https://www.jobkorea.co.kr/Recruit/GI_Read/10000002 | https://www.jobkorea.co.kr/Recruit/GI_Read_Comt_Ifrm?Gno=10000002 | false
			https://www.jobkorea.co.kr/Recruit/GI_Read_Comt_Ifrm?Gno=10000002 | https://www.jobkorea.co.kr/Recruit/GI_Read/10000002 | https://www.jobkorea.co.kr/Recruit/GI_Read_Comt_Ifrm?Gno=10000002 | false
			https://www.wanted.co.kr/wd/123456 | https://www.wanted.co.kr/wd/123456 | "" | false
			https://jumpit.saramin.co.kr/position/9876 | https://jumpit.saramin.co.kr/position/9876 | "" | true
			https://careers.example.com/jobs/1 | https://careers.example.com/jobs/1 | "" | true
			https://www.saramin.co.kr/zf_user/company-info | https://www.saramin.co.kr/zf_user/company-info | "" | true
			""")
	void plansAreTheSameAsJobfit(String url, String main, String detail, boolean followIframes) {
		Plan plan = FetchPlans.plan(url);
		assertThat(plan.mainUrl()).isEqualTo(main);
		assertThat(plan.detailUrls()).isEqualTo(detail.isEmpty() ? List.of() : List.of(detail));
		assertThat(plan.followIframes()).isEqualTo(followIframes);
		// 사람인·잡코리아만 사용자가 준 주소를 Referer 로 보낸다(jobfit 과 같다)
		assertThat(plan.referer()).isEqualTo(detail.isEmpty() ? "" : url);
	}

	@Test
	void lookalikeHostGetsTheGenericPlan() {
		// 공고키가 사이트를 점 경계로 보므로(posting/PostingKeys) 남의 호스트에 사람인 주소를 대신 받지 않는다
		Plan plan = FetchPlans.plan("https://fakesaramin.co.kr/zf_user/jobs/view?rec_idx=1");
		assertThat(plan.mainUrl()).isEqualTo("https://fakesaramin.co.kr/zf_user/jobs/view?rec_idx=1");
		assertThat(plan.detailUrls()).isEmpty();
	}

	@Test
	void firstSameHostIframe() {
		URI base = URI.create("https://careers.example.com/jobs/1");
		String html = "<iframe src=\"about:blank\"></iframe><iframe src=\"https://ads.example.net/x\"></iframe>"
				+ "<iframe src=\"\" data-src=\"/jobs/1/detail\"></iframe><iframe src=\"/jobs/1/other\"></iframe>";
		assertThat(FetchPlans.sameHostIframe(HtmlText.parse(html), base)).contains("https://careers.example.com/jobs/1/detail");
		assertThat(FetchPlans.sameHostIframe(HtmlText.parse("<iframe src=\"https://other.example.com/a\"></iframe>"), base)).isEmpty();
		assertThat(FetchPlans.sameHostIframe(HtmlText.parse("<p>no frames</p>"), base)).isEmpty();
	}

}
