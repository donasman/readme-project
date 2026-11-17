package com.readme.posting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.keyword.KeywordDictionary;

/**
 * 공고를 올리는 본문. POST /api/groups/{groupId}/posts(그룹에 올리기, 설계 4.4)와 POST /api/me/postings(그룹 없이 내 공고에 넣기,
 * 설계 4.2)가 같이 쓴다. 회사명, 공고명, 마감이 필수다. 길이 제한은 테이블 칸의 길이와 같다.
 * 원문 주소는 http·https 만 받는다(설계 6절 12번). 빈 글자는 넣지 않은 것으로 본다.
 * 역슬래시도 받지 않는다. 브라우저는 \ 를 / 로 읽어 다른 호스트를 여는데, 공고키는 뒤쪽 호스트로 만들어진다.
 *
 * <p>키워드와 공고 내용 묶음은 넣지 않아도 된다(설계 4.5). 서버는 읽어 오기의 결과를 들고 있지 않으므로 받은 값을 다시 검사한다:
 * 키워드 100개(하나 100자, posting_keywords.keyword 칸), 묶음 100개(이름 50자), 묶음의 글자는 모두 합쳐 50,000자
 * (읽어 오기가 받는 본문의 한도)까지다.
 */
public record UploadRequest(@NotBlank @Size(max = 200) String company, @NotBlank @Size(max = 300) String title,
		@NotNull @Valid DeadlineInput deadline, @Size(max = 50) String deadlineName, @Size(max = 100) String applyAt,
		@Size(max = 2000) @Pattern(regexp = "(?i)(https?://[^\\s\\\\]+)?") String sourceUrl,
		@Size(max = 100) List<@Size(max = 100) String> keywords, @Size(max = 100) List<@Valid Section> sections) {

	/** 묶음의 이름과 항목을 모두 더한 글자 수의 한도. 공고 읽어 오기가 받는 본문의 한도(설계 4.6)와 같다. */
	static final int MAX_SECTION_TEXT = 50_000;

	/**
	 * 저장할 값. 앞뒤 공백을 지운다. 마감의 짝이 맞지 않으면(날짜 없는 date, 날짜 있는 always·unknown) 400 INVALID_INPUT.
	 * 키워드는 사전의 대표 표기로 바꾸고, 사전에 없거나 빈 것은 빼고, 바꾼 뒤 겹치는 것은 처음 것만 둔다.
	 * 묶음은 빈 항목을 빼고, 항목이 없는 묶음을 뺀다. 묶음의 글자가 모두 합쳐 50,000자를 넘으면 400 INVALID_INPUT.
	 */
	public PostingDraft toDraft() {
		DeadlineInput checked = this.deadline.requireConsistent();
		return new PostingDraft(this.company.strip(), this.title.strip(), checked.kind(), checked.date(),
				blankToNull(this.deadlineName), blankToNull(this.applyAt), blankToNull(this.sourceUrl),
				cleanSections(this.sections), dictionaryKeywords(this.keywords));
	}

	private static List<PostSection> cleanSections(List<Section> sections) {
		List<PostSection> out = new ArrayList<>();
		int length = 0;
		for (Section section : orEmpty(sections)) {
			if (section == null) {
				continue;
			}
			List<String> items = orEmpty(section.items()).stream()
				.filter((item) -> item != null && !item.isBlank())
				.map(String::strip)
				.toList();
			if (items.isEmpty()) {
				continue;
			}
			String title = section.title().strip();
			length += title.length() + items.stream().mapToInt(String::length).sum();
			out.add(new PostSection(title, items));
		}
		if (length > MAX_SECTION_TEXT) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "공고 내용은 모두 합쳐 50,000자까지 넣을 수 있습니다");
		}
		return out;
	}

	/** 사전에 있는 키워드만, 대표 표기로, 처음 나온 순서대로 한 번씩. posting_keywords 는 사전의 대표 표기를 담는 칸이다. */
	private static List<String> dictionaryKeywords(List<String> keywords) {
		KeywordDictionary dictionary = KeywordDictionary.standard();
		LinkedHashSet<String> out = new LinkedHashSet<>();
		for (String keyword : orEmpty(keywords)) {
			if (keyword != null && dictionary.contains(keyword)) {
				out.add(dictionary.normalize(keyword));
			}
		}
		return List.copyOf(out);
	}

	private static <T> List<T> orEmpty(List<T> values) {
		return (values != null) ? values : List.of();
	}

	private static String blankToNull(String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	/**
	 * 공고 내용 한 묶음. 프런트의 PostSection 모양이다. 칸 확인 화면이 보내는 DraftSection 의 from 은 받지 않는다(버린다).
	 *
	 * @param items 빈 항목은 뺀다. 항목이 없으면 묶음을 뺀다
	 */
	public record Section(@NotBlank @Size(max = 50) String title, List<String> items) {
	}

}
