package com.readme.extraction;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 올린 자료에서 읽어 낸 본문(card_sources). 원본 파일은 두지 않는다(설계 가정 C2).
 * 같은 사람이 같은 본문을 다시 올리면 새 행을 만들지 않는다(unique (user_id, sha256)).
 *
 * <p>넣기는 CardSourceRepository 의 조건이 붙은 native insert 로 한다. 이 엔티티는 지우기와 뒤에 붙일 추출 작업이 쓴다.
 */
@Entity
@Table(name = "card_sources")
class CardSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	/** FILE, TEXT */
	private String kind;

	private String fileName;

	private String contentType;

	private Long byteSize;

	/** char(64). 다듬은 본문의 SHA-256 */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String sha256;

	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private String body;

	private Integer charCount;

	private Instant createdAt;

	protected CardSource() {
	}

}
