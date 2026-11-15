package com.readme.extraction;

import java.util.List;

/**
 * 카드 뽑기 작업(POST /api/card-sources/{id}/extract, GET /api/card-extractions/{jobId}). 프런트의 ExtractionJob 에 가깝다
 * (다른 곳은 경험카드 설계 6절).
 *
 * @param status        queued, running, done, failed
 * @param doneParts     마친 조각 수(프런트의 doneChunks)
 * @param totalParts    조각 수(프런트의 totalChunks)
 * @param cardsSoFar    이 작업이 만든 카드 가운데 남아 있는 수
 * @param newCards      끝났으면 cardsSoFar, 아니면 0
 * @param skippedSameAs 이미 있는 카드와 같은 경험이라 건너뛴 카드의, 그 이미 있는 카드의 제목들
 * @param error         failed 일 때 화면에 보일 이유. 아니면 null
 */
record ExtractionJobResponse(long id, long sourceId, String status, int doneParts, int totalParts, int cardsSoFar,
		int newCards, List<String> skippedSameAs, String error) {
}
