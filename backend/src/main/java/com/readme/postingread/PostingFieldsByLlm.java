package com.readme.postingread;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.readme.common.error.ApiException;
import com.readme.llm.Llm;
import com.readme.llm.LlmErrorCode;
import com.readme.llm.LlmFeature;
import com.readme.posting.DeadlineKind;
import com.readme.postingread.PostingReadResponse.DeadlineValue;

/**
 * 규칙으로 못 읽은 필수 칸(회사명, 공고명, 마감)을 LLM 으로 채운다(공고 설계 4.6, 화면·API 대응 3절 7번).
 *
 * <ul>
 * <li>필수 칸을 모두 읽었으면 부르지 않는다. 부를 때는 마감의 이름과 지원처도 함께 묻는다(규칙은 이 둘을 읽지 않는다).</li>
 * <li>규칙이 읽은 칸은 바꾸지 않는다.</li>
 * <li>원문의 앞 8,000자만 보낸다. 필수 칸은 대개 앞에 있고, 50,000자를 다 보내면 한 번에 0.3달러쯤 든다.</li>
 * <li>한도에 닿았거나 LLM 이 실패하면 규칙으로 읽은 결과를 그대로 돌려준다(읽기 자체는 실패하지 않는다).</li>
 * <li>근거(evidence)는 원문에 그대로 있을 때만 싣는다. 값이 올리기의 칸 길이를 넘으면 버린다.</li>
 * </ul>
 */
@Component
class PostingFieldsByLlm {

	private static final Logger log = LoggerFactory.getLogger(PostingFieldsByLlm.class);

	private static final Set<String> REQUIRED = Set.of("company", "title", "deadline");

	static final int MAX_CHARS = 8_000;

	private static final String PROMPT = prompt();

	private final Llm llm;

	PostingFieldsByLlm(Llm llm) {
		this.llm = llm;
	}

	/** LLM 이 읽을 칸. 모든 칸이 늘 있고, 글에 없으면 value 가 "" 다. */
	record FieldsAnswer(Field company, Field title, DeadlineAnswer deadline, Field deadlineName, Field applyAt) {

		record Field(String value, String evidence) {
		}

		/** @param kind "date", "always", "unknown" */
		record DeadlineAnswer(String kind, String date, String evidence) {
		}

	}

	PostingReadResponse fill(PostingReadResponse read, long userId, LocalDate today) {
		String text = read.source().text();
		if (text.isBlank() || read.unread().stream().noneMatch(REQUIRED::contains)) {
			return read;
		}
		FieldsAnswer answer;
		try {
			this.llm.requireBudget(userId);
			answer = this.llm.ask(userId, LlmFeature.POSTING_FIELDS, PROMPT, question(read, today), FieldsAnswer.class);
		}
		catch (ApiException ex) {
			if (!(ex.errorCode() instanceof LlmErrorCode)) {
				throw ex;
			}
			log.debug("공고 칸을 LLM 으로 채우지 않았다: {}", ex.errorCode().name());
			return read;
		}
		return merge(read, answer);
	}

	private static String question(PostingReadResponse read, LocalDate today) {
		String text = read.source().title() + "\n" + read.source().text();
		int end = text.offsetByCodePoints(0, Math.min(MAX_CHARS, text.codePointCount(0, text.length())));
		return "오늘: " + today + "\n\n[공고 글]\n" + text.substring(0, end);
	}

	/** 읽지 못한 칸만 채운다. */
	static PostingReadResponse merge(PostingReadResponse read, FieldsAnswer answer) {
		String text = read.source().title() + "\n" + read.source().text();
		Map<String, String> evidence = new LinkedHashMap<>(read.evidence());
		List<String> unread = new ArrayList<>(read.unread());
		String company = take("company", read.company(), answer.company(), 200, text, evidence, unread);
		String title = take("title", read.title(), answer.title(), 300, text, evidence, unread);
		String deadlineName = take("deadlineName", read.deadlineName(), answer.deadlineName(), 50, text, evidence, unread);
		String applyAt = take("applyAt", read.applyAt(), answer.applyAt(), 100, text, evidence, unread);
		DeadlineValue deadline = read.deadline();
		if (unread.contains("deadline")) {
			DeadlineValue filled = deadline(answer.deadline());
			if (filled != null) {
				deadline = filled;
				unread.remove("deadline");
				putEvidence("deadline", answer.deadline().evidence(), text, evidence);
			}
		}
		return new PostingReadResponse(company, title, deadline, deadlineName, applyAt, read.sourceUrl(), evidence,
				unread, read.sections(), read.keywords(), read.source(), read.duplicate());
	}

	private static String take(String field, String current, FieldsAnswer.Field answer, int maxLength, String text,
			Map<String, String> evidence, List<String> unread) {
		if (!unread.contains(field) || answer == null || answer.value() == null) {
			return current;
		}
		String value = answer.value().strip();
		if (value.isEmpty() || value.length() > maxLength) {
			return current;
		}
		unread.remove(field);
		putEvidence(field, answer.evidence(), text, evidence);
		return value;
	}

	/** 읽을 수 없는 마감(unknown, 날짜가 틀림)이면 null. */
	private static DeadlineValue deadline(FieldsAnswer.DeadlineAnswer answer) {
		if (answer == null || answer.kind() == null) {
			return null;
		}
		if (answer.kind().equals("always")) {
			return new DeadlineValue(DeadlineKind.ALWAYS, null);
		}
		if (answer.kind().equals("date") && answer.date() != null) {
			try {
				return new DeadlineValue(DeadlineKind.DATE, LocalDate.parse(answer.date().strip()));
			}
			catch (DateTimeParseException ex) {
				return null;
			}
		}
		return null;
	}

	private static void putEvidence(String field, String snippet, String text, Map<String, String> evidence) {
		if (snippet != null && !snippet.isBlank() && text.contains(snippet.strip())) {
			evidence.put(field, snippet.strip());
		}
	}

	private static String prompt() {
		try (InputStream in = new ClassPathResource("prompts/posting-fields.md").getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프롬프트를 읽지 못했다: posting-fields.md", ex);
		}
	}

}
