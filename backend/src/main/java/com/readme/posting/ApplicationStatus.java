package com.readme.posting;

/**
 * 한 사람의 한 공고 지원 상태(user_postings 의 decision, stage, result).
 *
 * <p>단계와 결과는 지원함일 때만 있다(테이블 제약).
 */
public record ApplicationStatus(Decision decision, Stage stage, Result result) {

	public enum Decision {
		/** 미정 */
		UNDECIDED,
		/** 보류 */
		HOLD,
		/** 지원안함 */
		SKIP,
		/** 지원함 */
		APPLIED
	}

	public enum Stage {

		DOCUMENT("서류"), TEST("필기"), INTERVIEW_1("1차 면접"), INTERVIEW_2("2차 면접"), FINAL("최종");

		private final String label;

		Stage(String label) {
			this.label = label;
		}

	}

	public enum Result {
		/** 진행 중 */
		IN_PROGRESS,
		/** 합격 */
		PASSED,
		/** 불합격 */
		FAILED
	}

	/** DB 의 세 칸으로 만든다. 내 공고 행이 없으면(decision 이 null) null 을 돌려준다. */
	public static ApplicationStatus of(String decision, String stage, String result) {
		if (decision == null) {
			return null;
		}
		return new ApplicationStatus(Decision.valueOf(decision), (stage != null) ? Stage.valueOf(stage) : null,
				(result != null) ? Result.valueOf(result) : null);
	}

	public boolean applied() {
		return this.decision == Decision.APPLIED;
	}

	/**
	 * 화면 글자(설계 5.3). 지원함이 아니면 null 이다.
	 * 서류 진행 중은 "지원함", 다른 단계 진행 중은 "필기 단계", 결과가 나오면 "서류 합격", "1차 면접 불합격".
	 */
	public String label() {
		if (!applied()) {
			return null;
		}
		return switch (this.result) {
			case IN_PROGRESS -> (this.stage == Stage.DOCUMENT) ? "지원함" : this.stage.label + " 단계";
			case PASSED -> this.stage.label + " 합격";
			case FAILED -> this.stage.label + " 불합격";
		};
	}

	/** "단계·결과까지"를 연 멤버에게 더 보이는 글자. 그냥 "지원함"이면 지원 표시로 충분하므로 null 이다(설계 5.2). */
	public String progressLabel() {
		String label = label();
		return (label == null || (this.stage == Stage.DOCUMENT && this.result == Result.IN_PROGRESS)) ? null : label;
	}

	/** 상태가 없을 수 있을 때 쓰는 {@link #label()}. */
	public static String labelOf(ApplicationStatus status) {
		return (status != null) ? status.label() : null;
	}

}
