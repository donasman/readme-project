package com.readme.inboundmail;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** 보내는 주소 하나(mail_senders, 메일 설계 M5). 소문자로 둔다. 넣기와 지우기는 리포지토리의 native 쓰기로 한다. */
@Entity
@Table(name = "mail_senders")
@IdClass(RegisteredSender.Key.class)
class RegisteredSender {

	@Id
	private Long userId;

	@Id
	private String email;

	private Instant createdAt;

	protected RegisteredSender() {
	}

	record Key(Long userId, String email) implements Serializable {
	}

}
