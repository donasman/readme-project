package com.readme.posting;

import java.time.LocalDate;

import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고의 전형 일정 하나(메일 설계 4.5). 달력에 보인다.
 *
 * @param time 시각("HH:mm"). 메일에 없었으면 null
 */
record ScheduleResponse(long postingId, String company, String title, Stage stage, LocalDate date, String time) {
}
