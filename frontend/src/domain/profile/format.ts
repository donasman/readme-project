import type { Profile } from "./types"

/** 경력 길이: "연차 2년 (33개월)". 1년이 안 되면 "연차 5개월". */
export const formatCareerLength = ({ careerMonths, careerYears }: Pick<Profile, "careerMonths" | "careerYears">): string =>
  careerYears > 0 ? `연차 ${careerYears}년 (${careerMonths}개월)` : `연차 ${careerMonths}개월`
