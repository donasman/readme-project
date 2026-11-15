package com.readme.posting;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.readme.common.error.ApiException;
import com.readme.keyword.KeywordDictionary;
import com.readme.llm.Llm;
import com.readme.llm.LlmFeature;
import com.readme.posting.PostingAnalysisRules.Answer;
import com.readme.posting.PostingAnalysisRules.Checked;
import com.readme.posting.PostingAnalysisRules.Input;

/**
 * 공고 분석(공고 설계 4.7, 남은 작업 6-2). 공고를 그룹에 올리거나 내 공고에 넣은 트랜잭션이 커밋되면, 그 공고에 분석이 없을 때
 * 뒤에서 한 번 돌린다. 분석은 공고 본체(postings.analysis)에 두고 같은 공고를 쓰는 그룹과 내 공고가 함께 본다.
 *
 * <ul>
 * <li>사용액은 올리거나 넣은 사람에게 든다. 그 사람이 이번 달 한도에 닿았으면 돌리지 않는다. 다음에 누가 올리거나 넣을 때 다시 본다.</li>
 * <li>공고 내용 묶음이 없으면 돌리지 않는다. 원문은 저장하지 않아 보낼 글이 없다.</li>
 * <li>같은 공고를 둘이 함께 돌리지 않는다. 시작할 때 조건이 붙은 update 로 공고를 잡는다(analysis_started_at).</li>
 * <li>실패(LLM 오류, 쓸 수 없는 답)는 기록만 하고 잡은 것을 놓는다. 올리기와 넣기의 응답에는 영향이 없다.</li>
 * <li>LLM 은 트랜잭션 밖에서 부른다.</li>
 * </ul>
 */
@Component
class PostingAnalyses {

	private static final Logger log = LoggerFactory.getLogger(PostingAnalyses.class);

	/**
	 * 이보다 오래 끝나지 않은 분석은 멈춘 것(서버가 다시 떴다)으로 보고 다음 사람이 다시 잡는다. 한 번의 분석은 대개 몇 분 안에 끝난다.
	 */
	static final Duration STALE = Duration.ofMinutes(10);

	/** 지시문. 개인의 경험 태그 대신 키워드 사전의 대표 표기를 싣는다(정리 문서 5절). 그래서 분석이 누구에게도 기대지 않는다. */
	static final String PROMPT = prompt();

	private static final TypeReference<List<PostSection>> SECTIONS = new TypeReference<>() {
	};

	private final Postings postings;

	private final Llm llm;

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	PostingAnalyses(Postings postings, Llm llm, JdbcClient jdbc, JsonMapper jsonMapper, Clock clock) {
		this.postings = postings;
		this.llm = llm;
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/**
	 * 공고를 올리거나 넣었다({@link Postings#findOrCreate}). 커밋된 뒤에만 받는다(되돌려진 올리기는 분석하지 않는다).
	 *
	 * @param userId 올리거나 넣은 사람. 사용액이 이 사람에게 든다
	 */
	record Requested(long postingId, long userId) {
	}

	/** 공고에 분석이 없으면 분석한다. 요청 스레드가 아니라 뒤에서 돈다. */
	// shortcut: 할 일을 서버의 메모리에만 두어 서버가 내려가면 돌던 분석과 기다리던 분석이 사라진다, 다음에 그 공고를 올리거나 넣으면 다시 돈다(10분 뒤). 놓치는 것이 문제가 되면 할 일 테이블을 둔다
	@Async
	@TransactionalEventListener
	void analyze(Requested request) {
		try {
			run(request.postingId(), request.userId());
		}
		catch (ApiException ex) {
			log.info("공고 {} 를 분석하지 않았다: {}", request.postingId(), ex.errorCode().name());
		}
		catch (RuntimeException ex) {
			log.warn("공고 {} 를 분석하지 못했다", request.postingId(), ex);
		}
	}

	private void run(long postingId, long userId) {
		Source source = source(postingId).orElse(null);
		if (source == null || source.analyzed() || source.sections().isEmpty()) {
			return;
		}
		this.llm.requireBudget(userId);
		// DB 의 timestamptz 는 마이크로초까지라, 놓을 때 같은 값으로 찾도록 잘라 둔다
		Instant startedAt = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
		if (!this.postings.startAnalysis(postingId, startedAt, startedAt.minus(STALE))) {
			return;
		}
		try {
			Input input = PostingAnalysisRules.input(source.company(), source.title(), source.sections(),
					KeywordDictionary.standard());
			Answer answer = this.llm.ask(userId, LlmFeature.POSTING_ANALYSIS, PROMPT, input.text(), Answer.class);
			Checked checked = PostingAnalysisRules.check(answer, input.full(), KeywordDictionary.standard());
			if (checked.analysis() == null) {
				log.warn("공고 {} 의 분석 답을 쓸 수 없다: {}", postingId, checked.errors());
				return;
			}
			if (!checked.dropped().isEmpty()) {
				log.info("공고 {} 의 분석에서 공고 글에 없는 문항 {}개를 버렸다", postingId, checked.dropped().size());
			}
			this.postings.saveAnalysis(postingId, this.jsonMapper.writeValueAsString(checked.analysis()));
		}
		finally {
			this.postings.endAnalysis(postingId, startedAt);
		}
	}

	private Optional<Source> source(long postingId) {
		return this.jdbc.sql("""
				select company, title, sections::text as sections, analysis is not null as analyzed
				  from postings where id = :postingId
				""")
			.param("postingId", postingId)
			.query((rs, rowNum) -> new Source(rs.getString("company"), rs.getString("title"),
					this.jsonMapper.readValue(rs.getString("sections"), SECTIONS), rs.getBoolean("analyzed")))
			.optional();
	}

	/** 분석에 쓰는 공고 본체의 값. */
	private record Source(String company, String title, List<PostSection> sections, boolean analyzed) {
	}

	private static String prompt() {
		try (InputStream in = new ClassPathResource("prompts/posting-analysis.md").getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("{{dictionary}}",
					String.join(", ", KeywordDictionary.standard().canonicals()));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프롬프트를 읽지 못했다: posting-analysis.md", ex);
		}
	}

}
