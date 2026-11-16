package com.readme.inboundmail;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 사람마다 수신 주소의 토큰 하나(mail_inboxes, 메일 설계 M4). 만들기와 바꾸기는 리포지토리의 native 쓰기로 한다. */
@Entity
@Table(name = "mail_inboxes")
class MailInbox {

	@Id
	private Long userId;

	private String token;

	private Instant createdAt;

	private Instant updatedAt;

	protected MailInbox() {
	}

}
