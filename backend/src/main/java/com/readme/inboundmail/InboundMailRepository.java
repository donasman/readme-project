package com.readme.inboundmail;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InboundMailRepository extends JpaRepository<InboundMail, Long> {

	/** 같은 메일(message_key)이 없을 때만 넣는다. 넣었으면 1. */
	@Modifying
	@Query(value = """
			insert into inbound_mails (user_id, message_key, sender, subject, received_at, status, body, code)
			values (:userId, :messageKey, :sender, :subject, :receivedAt, :status, :body, :code)
			on conflict (user_id, message_key) do nothing
			""", nativeQuery = true)
	int insertIfNew(@Param("userId") long userId, @Param("messageKey") String messageKey, @Param("sender") String sender,
			@Param("subject") String subject, @Param("receivedAt") Instant receivedAt, @Param("status") String status,
			@Param("body") String body, @Param("code") String code);

	@Query("select m.id from InboundMail m where m.userId = :userId and m.messageKey = :messageKey")
	Optional<Long> findIdByKey(@Param("userId") long userId, @Param("messageKey") String messageKey);

	Optional<InboundMail> findByIdAndUserId(long id, long userId);

	/**
	 * 분류를 맡는다. 기다리는 메일이거나, staleBefore 보다 먼저 잡고 끝나지 않은 메일만 잡는다. 둘이 같이 잡지 못한다. 잡았으면 1.
	 */
	@Modifying
	@Query(value = """
			update inbound_mails set status = 'READING', reading_started_at = :now
			 where id = :id
			   and (status = 'WAITING' or (status = 'READING' and reading_started_at < :staleBefore))
			""", nativeQuery = true)
	int claim(@Param("id") long id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

	/** 잡은 분류를 놓아 다시 기다리게 한다. 그 사이 다른 쪽이 다시 잡았으면(시작 시각이 다르면) 그대로 둔다. */
	@Modifying
	@Query(value = """
			update inbound_mails set status = 'WAITING', reading_started_at = null
			 where id = :id and status = 'READING' and reading_started_at = :startedAt
			""", nativeQuery = true)
	int release(@Param("id") long id, @Param("startedAt") Instant startedAt);

	/** 분류할 메일: 기다리는 것과 멈춘 것. 오래된 것부터. */
	@Query(value = """
			select id from inbound_mails
			 where status = 'WAITING' or (status = 'READING' and reading_started_at < :staleBefore)
			 order by id limit :limit
			""", nativeQuery = true)
	List<Long> findToRead(@Param("staleBefore") Instant staleBefore, @Param("limit") int limit);

	/** 확인 필요를 반영·무시로 둔다. 아직 확인을 기다리는 내 메일일 때만 1. */
	@Modifying
	@Query(value = """
			update inbound_mails set review = :review
			 where id = :id and user_id = :userId and status = 'READ' and review = 'PENDING'
			""", nativeQuery = true)
	int review(@Param("id") long id, @Param("userId") long userId, @Param("review") String review);

	/** 새로 넣은 공고를 되돌린 것으로 둔다. 확인을 기다리던 것은 무시로 둔다. 되돌릴 수 있는 내 메일일 때만 1. */
	@Modifying
	@Query(value = """
			update inbound_mails
			   set reverted = true, review = case when review = 'PENDING' then 'IGNORED' else review end
			 where id = :id and user_id = :userId and status = 'READ' and added and not reverted
			""", nativeQuery = true)
	int revert(@Param("id") long id, @Param("userId") long userId);

	/** Gmail 전달 확인 메일을 지운다. 내 것일 때만 1. */
	@Modifying
	@Query(value = "delete from inbound_mails where id = :id and user_id = :userId and status = 'FORWARDING'",
			nativeQuery = true)
	int deleteForwarding(@Param("id") long id, @Param("userId") long userId);

	/** 분류하지 못하고 before 보다 먼저 받은 메일을 지운다(본문 글과 함께). */
	@Modifying
	@Query(value = "delete from inbound_mails where status in ('WAITING', 'READING') and received_at < :before",
			nativeQuery = true)
	int deleteUnread(@Param("before") Instant before);

	/** before 보다 먼저 받은, 확인을 기다리지 않는 메일을 지운다. */
	@Modifying
	@Query(value = """
			delete from inbound_mails
			 where status in ('READ', 'FORWARDING') and received_at < :before and review is distinct from 'PENDING'
			""", nativeQuery = true)
	int deleteOld(@Param("before") Instant before);

}
