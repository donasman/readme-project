package com.readme.inboundmail;

/**
 * LLM 이 메일에서 읽는 칸(메일 설계 4.3, 지시문 prompts/mail-classify.md). 모든 칸이 늘 있고, 없으면 "" 이고 postingId 는 0 이다.
 *
 * @param recruiting   받은 사람의 한 공고에 관한 채용 메일인지
 * @param postingId    내 공고 목록의 id. 없으면 0
 * @param company      목록에 없는 공고의 회사명
 * @param title        목록에 없는 공고의 공고명
 * @param deadline     목록에 없는 공고의 마감일 "YYYY-MM-DD"
 * @param stage        document, test, interview_1, interview_2, final
 * @param result       in_progress, passed, failed
 * @param scheduleDate 그 단계의 날짜 "YYYY-MM-DD"
 * @param scheduleTime 그 단계의 시각 "HH:mm"
 * @param quote        근거 문장
 * @param summary      확인함에 보일 짧은 이름
 */
record MailAnswer(boolean recruiting, long postingId, String company, String title, String deadline, String stage,
		String result, String scheduleDate, String scheduleTime, String quote, String summary) {
}
