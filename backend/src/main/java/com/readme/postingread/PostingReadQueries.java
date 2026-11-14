package com.readme.postingread;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.common.config.TimeConfig;
import com.readme.group.MemberResponse;
import com.readme.posting.PostingKeys;
import com.readme.postingread.PostingReadResponse.Duplicate;

/**
 * 읽는 단계의 같은 공고 안내(화면·API 대응 문서 3절 9번). 링크의 공고키로 그 그룹에 내리지 않은 같은 공고가 있는지 본다.
 */
@Component
class PostingReadQueries {

	/** postings.posting_key 칸의 길이. 더 긴 키는 저장되지 않으므로(posting/Postings) 찾지 않는다. */
	private static final int MAX_KEY_LENGTH = 300;

	private static final String SAME_POST = """
			select p.id, p.company, p.title, gp.posted_at, u.id as posted_by_id, u.display_name as posted_by_name
			  from postings p
			  join group_posts gp on gp.posting_id = p.id
			  left join users u on u.id = gp.posted_by
			 where p.posting_key = :key
			   and gp.group_id = :groupId
			   and gp.taken_down_at is null
			""";

	private final JdbcClient jdbc;

	PostingReadQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** link 가 없거나 공고키가 너무 길면 빈 값. */
	Optional<Duplicate> sameInGroup(long groupId, String link) {
		if (link == null || link.isBlank()) {
			return Optional.empty();
		}
		String key = PostingKeys.derive(link, null, null, null).key();
		if (key.length() > MAX_KEY_LENGTH) {
			return Optional.empty();
		}
		return this.jdbc.sql(SAME_POST).param("key", key).param("groupId", groupId).query((rs, rowNum) -> {
			Long postedBy = rs.getObject("posted_by_id", Long.class);
			return new Duplicate(rs.getLong("id"), rs.getString("company"), rs.getString("title"),
					(postedBy != null) ? new MemberResponse(postedBy, rs.getString("posted_by_name")) : null,
					LocalDate.ofInstant(rs.getObject("posted_at", OffsetDateTime.class).toInstant(), TimeConfig.SEOUL));
		}).optional();
	}

}
