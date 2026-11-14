package com.readme.extraction;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CardSourceRepository extends JpaRepository<CardSource, Long> {

	/**
	 * 이 사람이 같은 본문을 올린 적이 없을 때만 넣는다. 같은 자료를 두 번 같이 올려도 한 행만 생긴다. 넣었으면 1, 이미 있었으면 0.
	 */
	@Modifying
	@Query(value = """
			insert into card_sources (user_id, kind, file_name, content_type, byte_size, sha256, body, char_count,
			                          created_at)
			values (:userId, :kind, cast(:fileName as varchar), cast(:contentType as varchar), cast(:byteSize as bigint),
			        :sha256, :body, :charCount, :now)
			on conflict (user_id, sha256) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("userId") long userId, @Param("kind") String kind, @Param("fileName") String fileName,
			@Param("contentType") String contentType, @Param("byteSize") Long byteSize, @Param("sha256") String sha256,
			@Param("body") String body, @Param("charCount") int charCount, @Param("now") Instant now);

	@Query("select s.id from CardSource s where s.userId = :userId and s.sha256 = :sha256")
	Optional<Long> findIdByUserIdAndSha256(@Param("userId") long userId, @Param("sha256") String sha256);

	/** 내 자료를 지운다. 지웠으면 1, 내 자료가 아니거나 없으면 0. 추출 작업은 함께 지워지고 카드는 남는다(테이블의 on delete). */
	@Modifying
	@Query("delete from CardSource s where s.id = :id and s.userId = :userId")
	int deleteMine(@Param("id") long id, @Param("userId") long userId);

}
