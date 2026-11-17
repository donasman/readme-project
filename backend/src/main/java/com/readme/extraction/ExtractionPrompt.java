package com.readme.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;

/**
 * 카드 추출의 프롬프트(경험카드 설계 4.6). 지시문(resources/prompts/exp-extract.md)은 늘 같고, 조각마다 바뀌는 것(이미 있는 카드,
 * 태그 표기, 자료 조각)은 사용자 글에 넣는다. 지시문이 같으면 나중에 프롬프트 캐시를 붙이기 쉽다.
 *
 * <p>jobfit 의 ai/exp_extract.py 의 prepare(사용자 글 만들기)와 fit_existing, promptfile.py 의 fit_list 를 옮겼다.
 */
final class ExtractionPrompt {

	static final String SYSTEM = read("prompts/exp-extract.md");

	/** 태그 표기 목록의 글자 수 상한(jobfit 의 TAGS_MAX_CHARS) */
	static final int TAGS_BUDGET = 600;

	/** 이미 있는 카드 목록의 글자 수 상한. jobfit 은 프롬프트 8,000자에서 남는 만큼이었다 */
	static final int EXISTING_BUDGET = 2_000;

	/** 직무 이름이라 태그 표기에 넣지 않는 낱말(jobfit match.py 의 ROLE_WORDS) */
	private static final Set<String> ROLE_WORDS = Set.of("백엔드", "프런트엔드", "풀스택", "서버 개발", "데이터 엔지니어");

	private static final Pattern NOT_TITLE = Pattern.compile("[\\s\\W_]+", Pattern.UNICODE_CHARACTER_CLASS);

	private ExtractionPrompt() {
	}

	/**
	 * 조각 하나의 사용자 글.
	 *
	 * @param existingTitles 이미 있는 내 카드. 경험 이름이 있으면 "경험 — 제목"이다
	 * @param tagHints       태그 표기 후보: 자료 조각에 나온 사전 낱말, 그다음 내 카드의 태그
	 */
	static String question(List<String> existingTitles, List<String> tagHints, String sourceName, int part, int parts,
			String text) {
		List<String> tags = tagHints.stream().distinct().filter((tag) -> !ROLE_WORDS.contains(tag)).toList();
		return "## 이미 있는 카드\n" + fitExisting(existingTitles, EXISTING_BUDGET) + "\n\n## 태그 표기 (참고)\n"
				+ fitList(tags, TAGS_BUDGET) + "\n\n## 자료 — " + ((sourceName != null) ? sourceName : "붙여넣은 글")
				+ ((parts > 1) ? " (조각 " + part + "/" + parts + ")" : "") + "\n" + text;
	}

	/**
	 * 이미 있는 카드 목록(jobfit 의 fit_existing). 리드미의 카드에는 jobfit 의 영문 id 가 없어 제목만 쓴다. 자리가 모자라면 앞에서부터
	 * 자르고 "외 N개"를 붙인다. 목록이 잘려도 같은 제목의 카드는 저장할 때 건너뛴다.
	 */
	static String fitExisting(List<String> titles, int budget) {
		if (titles.isEmpty()) {
			return "(없음)";
		}
		List<String> lines = titles.stream().map((title) -> "- " + title).toList();
		String full = String.join("\n", lines);
		if (full.length() <= budget) {
			return full;
		}
		String note = "- … 외 %d개 (제목이 같은 카드는 저장할 때 건너뜁니다)";
		List<String> out = new ArrayList<>();
		int used = note.length() + 4;
		for (String line : lines) {
			if (used + line.length() + 1 > budget) {
				break;
			}
			out.add(line);
			used += line.length() + 1;
		}
		out.add(note.formatted(lines.size() - out.size()));
		return String.join("\n", out);
	}

	/** 앞에서부터 budget 글자 안에 들어가는 만큼, 나머지는 "외 N개"(jobfit 의 fit_list). */
	static String fitList(List<String> items, int budget) {
		if (items.isEmpty()) {
			return "(없음)";
		}
		String sep = ", ";
		List<String> out = new ArrayList<>();
		int used = 0;
		for (String item : items) {
			int tail = (sep + "외 " + items.size() + "개").length();
			if (used + item.length() + sep.length() + tail > budget && !out.isEmpty()) {
				break;
			}
			out.add(item);
			used += item.length() + sep.length();
		}
		int rest = items.size() - out.size();
		return String.join(sep, out) + ((rest > 0) ? sep + "외 " + rest + "개" : "");
	}

	/** 같은 경험인지 볼 때의 제목: NFKC, 공백·기호를 지우고 소문자(jobfit 의 _norm_title). */
	static String normalizeTitle(String title) {
		return NOT_TITLE.matcher(Normalizer.normalize(title, Normalizer.Form.NFKC)).replaceAll("").toLowerCase(Locale.ROOT);
	}

	private static String read(String path) {
		try (InputStream in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프롬프트를 읽지 못했다: " + path, ex);
		}
	}

}
