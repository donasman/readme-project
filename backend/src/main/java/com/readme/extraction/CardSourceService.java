package com.readme.extraction;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.error.ApiException;
import com.readme.common.token.Tokens;

/**
 * 자료를 저장하고 지운다. 파일을 읽는 일(SourceInput)은 이 트랜잭션 밖에서 한다. 큰 파일을 읽는 동안 DB 연결(풀 5개)을 잡지 않는다.
 * 응답에 실을 자료는 트랜잭션이 끝난 뒤 컨트롤러가 CardSourceQueries 로 읽는다.
 */
@Service
class CardSourceService {

	private final CardSourceRepository sources;

	private final Clock clock;

	CardSourceService(CardSourceRepository sources, Clock clock) {
		this.sources = sources;
		this.clock = clock;
	}

	/**
	 * 자료를 저장한다. 같은 사람이 같은 본문(다듬은 본문의 SHA-256 이 같은 것)을 이미 올렸으면 새로 만들지 않고 그 자료를 가리킨다.
	 * 파일 이름이 달라도, 같은 글을 붙여넣어도 같은 자료다.
	 */
	@Transactional
	Saved save(long userId, SourceInput input) {
		String sha256 = Tokens.sha256Hex(input.text());
		int inserted = this.sources.insertIfAbsent(userId, input.kind().name(), input.fileName(), input.contentType(),
				input.byteSize(), sha256, input.text(), input.chars(), this.clock.instant());
		long id = this.sources.findIdByUserIdAndSha256(userId, sha256).orElseThrow();
		return new Saved(id, inserted == 0);
	}

	/** 내 자료를 지운다. 남의 자료나 없는 자료는 404 SOURCE_NOT_FOUND. 그 자료로 만든 카드는 남는다. */
	@Transactional
	void delete(long userId, long sourceId) {
		if (this.sources.deleteMine(sourceId, userId) == 0) {
			throw new ApiException(ExtractionErrorCode.SOURCE_NOT_FOUND);
		}
	}

	/** 저장한(또는 이미 있던) 자료의 id 와, 이미 올린 자료였는지. */
	record Saved(long id, boolean duplicate) {
	}

}
