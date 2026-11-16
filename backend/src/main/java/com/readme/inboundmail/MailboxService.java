package com.readme.inboundmail;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.error.ApiException;
import com.readme.posting.ApplicationStatus;
import com.readme.posting.UserPostings;

/** 확인함의 쓰기. 메일의 상태는 조건이 붙은 update 로 옮겨, 같은 메일을 두 번 반영하지 않는다. */
@Service
class MailboxService {

	private final InboundMailRepository mails;

	private final UserPostings userPostings;

	MailboxService(InboundMailRepository mails, UserPostings userPostings) {
		this.mails = mails;
		this.userPostings = userPostings;
	}

	@Transactional
	void apply(long userId, long id) {
		InboundMail mail = this.mails.findByIdAndUserId(id, userId).orElseThrow(MailboxService::notFound);
		ApplicationStatus proposal = mail.proposal();
		if (proposal == null || this.mails.review(id, userId, "APPLIED") == 0) {
			throw notFound();
		}
		this.userPostings.setStatus(userId, mail.postingId(), proposal);
		if (mail.scheduleDate() != null) {
			this.userPostings.setSchedule(userId, mail.postingId(), proposal.stage(), mail.scheduleDate(),
					mail.scheduleTime());
		}
	}

	@Transactional
	void ignore(long userId, long id) {
		if (this.mails.review(id, userId, "IGNORED") == 0 && this.mails.deleteForwarding(id, userId) == 0) {
			throw notFound();
		}
	}

	@Transactional
	void revert(long userId, long id) {
		InboundMail mail = this.mails.findByIdAndUserId(id, userId).orElseThrow(MailboxService::notFound);
		if (this.mails.revert(id, userId) == 0) {
			throw notFound();
		}
		this.userPostings.remove(userId, mail.postingId());
	}

	private static ApiException notFound() {
		return new ApiException(InboundMailErrorCode.MAIL_NOT_FOUND);
	}

}
