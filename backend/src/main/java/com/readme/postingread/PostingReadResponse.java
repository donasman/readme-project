package com.readme.postingread;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.readme.group.MemberResponse;
import com.readme.posting.DeadlineKind;
import com.readme.postingread.Deadlines.Read;

/**
 * 읽어 온 공고. 프런트의 PostDraft 에 가까운 모양이다(다른 곳은 공고 설계 8절). 서버는 이것을 저장하지 않는다.
 * 프런트가 들고 있다가 고친 값으로 올린다(POST /api/groups/{groupId}/posts).
 *
 * @param company      회사명. 못 읽었으면 ""
 * @param title        공고명. 못 읽었으면 ""
 * @param deadline     마감. 못 읽었으면 { "kind": "unknown" }
 * @param deadlineName 마감의 이름. 규칙으로는 읽지 않아 늘 ""
 * @param applyAt      지원처. 규칙으로는 아직 읽지 않아 늘 ""
 * @param sourceUrl    원문 링크. 링크로 읽었으면 그 링크, 본문이면 함께 받은 원문 링크, 없으면 ""
 * @param evidence     칸 → 그 값을 읽은 원문 조각(페이지 제목, JSON-LD 값, "마감일 2026.11.20", 붙여넣은 글의 첫 줄)
 * @param unread       읽지 못한 칸: company, title, deadline, deadlineName, applyAt, sourceUrl 가운데 빈 것
 * @param sections     공고 내용 묶음. title 은 화면의 묶음 이름, from 은 원문의 머리 줄
 * @param keywords     키워드 사전의 대표 표기(k8s → Kubernetes). 주요 업무·필수 요건·우대 사항에서 많이 나온 순으로 15개까지
 * @param source       원문: 페이지 제목(본문이면 첫 줄)과 메뉴를 걷어낸 글자 전체
 * @param duplicate    groupId 를 넣었고 링크의 공고가 이미 그 그룹에 있을 때만
 */
record PostingReadResponse(String company, String title, DeadlineValue deadline, String deadlineName, String applyAt,
		String sourceUrl, Map<String, String> evidence, List<String> unread, List<SectionValue> sections, List<String> keywords,
		Source source, @JsonInclude(JsonInclude.Include.NON_NULL) Duplicate duplicate) {

	/** 마감. 올리기의 deadline 과 같은 꼴: { "kind": "date", "date": "2026-11-20" }, { "kind": "always" }, { "kind": "unknown" }. */
	record DeadlineValue(DeadlineKind kind, @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate date) {
	}

	record SectionValue(String title, List<String> items, String from) {
	}

	record Source(String title, String text) {
	}

	/**
	 * 이미 그 그룹에 있는(내리지 않은) 같은 공고. 같은 공고는 링크의 공고키로 알아본다.
	 *
	 * @param postId   그 그룹 공고의 id(공고 본체 id)
	 * @param postedBy 올린 사람. 계정을 지웠으면 null
	 * @param postedAt 올린 날(한국 날짜)
	 */
	record Duplicate(long postId, String company, String title, MemberResponse postedBy, LocalDate postedAt) {
	}

	/** 읽은 값으로 응답을 만든다. 근거는 읽은 칸만, 읽지 못한 칸은 unread 에. */
	static PostingReadResponse read(Found company, Found title, Read deadline, String deadlineFrom, String sourceUrl,
			List<SectionValue> sections, List<String> keywords, Source source) {
		Map<String, String> evidence = new LinkedHashMap<>();
		List<String> unread = new ArrayList<>();
		check("company", company, evidence, unread);
		check("title", title, evidence, unread);
		check("deadline", deadline.known() ? new Found(deadline.kind().json(), deadlineFrom) : Found.NONE, evidence, unread);
		unread.add("deadlineName");
		unread.add("applyAt");
		if (sourceUrl.isEmpty()) {
			unread.add("sourceUrl");
		}
		return new PostingReadResponse(company.value(), title.value(), new DeadlineValue(deadline.kind(), deadline.date()),
				"", "", sourceUrl, evidence, unread, sections, keywords, source, null);
	}

	/** 링크의 공고가 이미 그룹에 있어 받으러 가지 않았을 때. 칸은 비어 있고 duplicate 만 뜻이 있다. */
	static PostingReadResponse notFetched(String url, Duplicate duplicate) {
		return read(Found.NONE, Found.NONE, Read.UNKNOWN, "", url, List.of(), List.of(), new Source("", "")).with(duplicate);
	}

	PostingReadResponse with(Duplicate duplicate) {
		return new PostingReadResponse(this.company, this.title, this.deadline, this.deadlineName, this.applyAt,
				this.sourceUrl, this.evidence, this.unread, this.sections, this.keywords, this.source, duplicate);
	}

	private static void check(String field, Found found, Map<String, String> evidence, List<String> unread) {
		if (found.value().isEmpty()) {
			unread.add(field);
		}
		else if (!found.from().isEmpty()) {
			evidence.put(field, found.from());
		}
	}

	/** 읽은 값과 그 근거. 못 읽었으면 둘 다 "". */
	record Found(String value, String from) {

		static final Found NONE = new Found("", "");

	}

}
