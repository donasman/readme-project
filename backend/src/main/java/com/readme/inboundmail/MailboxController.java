package com.readme.inboundmail;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.inboundmail.MailboxQueries.MailboxResponse;

/**
 * 메일 확인함(메일 설계 4.4). 내 메일이 아니거나 그 동작을 할 수 없는 메일이면 404 MAIL_NOT_FOUND.
 */
@RestController
class MailboxController {

	private final MailboxQueries queries;

	private final MailboxService actions;

	MailboxController(MailboxQueries queries, MailboxService actions) {
		this.queries = queries;
		this.actions = actions;
	}

	@GetMapping("/api/me/mailbox")
	MailboxResponse mailbox(@CurrentUser LoginUser user) {
		return this.queries.mailbox(user.id());
	}

	/** 확인 필요를 반영한다: 지원 상태를 바꾸고, 일정이 있으면 넣는다. */
	@PostMapping("/api/me/mailbox/{id}/apply")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void apply(@PathVariable long id, @CurrentUser LoginUser user) {
		this.actions.apply(user.id(), id);
	}

	/** 확인 필요나 Gmail 전달 확인 메일을 확인함에서 뺀다. */
	@PostMapping("/api/me/mailbox/{id}/ignore")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void ignore(@PathVariable long id, @CurrentUser LoginUser user) {
		this.actions.ignore(user.id(), id);
	}

	/** 메일로 새로 넣은 공고를 내 공고에서 뺀다(메일 설계 M12). */
	@PostMapping("/api/me/mailbox/{id}/revert")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revert(@PathVariable long id, @CurrentUser LoginUser user) {
		this.actions.revert(user.id(), id);
	}

}
