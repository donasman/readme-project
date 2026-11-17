package com.readme.card;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.card.CardQueries.CardFilter;
import com.readme.card.CardRequests.CardRequest;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.keyword.KeywordDictionary;

/**
 * 경험카드 API(설계 4.1). 내 카드만 다룬다. 남의 카드는 없는 카드와 같은 404 CARD_NOT_FOUND 다.
 * 카드 응답에는 언제나 검사 결과(issues)가 붙는다.
 */
@RestController
@RequestMapping("/api/cards")
class CardController {

	private final CardQueries queries;

	private final CardService service;

	CardController(CardQueries queries, CardService service) {
		this.queries = queries;
		this.service = service;
	}

	/**
	 * 내 카드 목록. 상세와 같은 모양이다(카드 확인 화면이 ?verified=false 로 받아 한 장씩 보여 준다).
	 * 거르기: tag(다른 표기도 받는다), type(문항 유형. 다른 이름도 받는다), verified, jobId(추출 작업), experience(경험 이름, 대소문자 무시),
	 * q(경험 이름·제목·내용·태그·근거 숫자에 든 글자).
	 */
	@GetMapping
	List<CardResponse> list(@RequestParam(required = false) String tag, @RequestParam(required = false) String type,
			@RequestParam(required = false) Boolean verified, @RequestParam(required = false) Long jobId,
			@RequestParam(required = false) String experience, @RequestParam(required = false) String q,
			@CurrentUser LoginUser user) {
		CardFilter filter = new CardFilter(isBlank(tag) ? null : KeywordDictionary.standard().normalize(tag),
				isBlank(type) ? null : questionType(type), verified, jobId, isBlank(experience) ? null : experience.strip(),
				isBlank(q) ? null : q.strip());
		return this.queries.list(user.id(), filter);
	}

	@GetMapping("/tags")
	List<CardTagResponse> tags(@CurrentUser LoginUser user) {
		return this.queries.tags(user.id());
	}

	/** 직접 만들기. 만든 카드를 돌려준다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CardResponse create(@Valid @RequestBody CardRequest request, @CurrentUser LoginUser user) {
		long cardId = this.service.create(user.id(), request.toInput());
		return detail(cardId, user);
	}

	@GetMapping("/{cardId}")
	CardResponse detail(@PathVariable long cardId, @CurrentUser LoginUser user) {
		return this.queries.find(user.id(), cardId).orElseThrow(() -> new ApiException(CardErrorCode.CARD_NOT_FOUND));
	}

	/** 고치기. 모든 칸을 보낸 값으로 바꾸고, 다시 검사한 카드를 돌려준다. */
	@PutMapping("/{cardId}")
	CardResponse update(@PathVariable long cardId, @Valid @RequestBody CardRequest request, @CurrentUser LoginUser user) {
		this.service.update(user.id(), cardId, request.toInput());
		return detail(cardId, user);
	}

	@DeleteMapping("/{cardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long cardId, @CurrentUser LoginUser user) {
		this.service.delete(user.id(), cardId);
	}

	/** 카드 확인. 확인한 카드를 돌려준다. */
	@PostMapping("/{cardId}/verify")
	CardResponse verify(@PathVariable long cardId, @CurrentUser LoginUser user) {
		this.service.verify(user.id(), cardId);
		return detail(cardId, user);
	}

	private static String questionType(String value) {
		String type = CardRules.normalizeType(value);
		if (type == null) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT,
					"문항 유형은 " + String.join(" · ", CardRules.QUESTION_TYPES) + " 중 하나로 넣어 주세요");
		}
		return type;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

}
