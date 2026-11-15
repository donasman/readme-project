package com.readme.extraction;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CardExtractionJobRepository extends JpaRepository<CardExtractionJob, Long> {

	/** 이 자료의 가장 최근 작업. */
	Optional<CardExtractionJob> findFirstBySourceIdOrderByCreatedAtDescIdDesc(long sourceId);

	/** 서버가 뜰 때 이어 돌릴 작업: 기다리거나 돌던 것. */
	@Query("select j.id from CardExtractionJob j where j.status in ('QUEUED', 'RUNNING') order by j.id")
	List<Long> findUnfinishedIds();

}
