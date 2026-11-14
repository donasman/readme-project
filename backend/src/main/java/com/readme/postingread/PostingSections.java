package com.readme.postingread;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고 글자에서 구조를 읽는다: 본문 유무, 섹션, 요약 칸. jobfit 의 src/jobfit/clean/sections.py 를 옮겼다.
 *
 * <p>판단은 모두 정규식이다. 글자 수로 본문 유무를 판단하지 않는다(메뉴만 1,510자인 페이지가 있었다, jobfit 실측).
 * Python 의 \s 는 전각 공백과 nbsp 도 맞으므로 정규식은 유니코드 글자 분류로 컴파일한다.
 * 자소서 문항 찾기(find_questions)는 공고 읽어 오기에 쓰지 않아 옮기지 않았다.
 */
final class PostingSections {

	/** 헤더 없이 시작하는 앞부분의 섹션 이름. */
	static final String HEAD = "머리";

	private static final int U = Pattern.UNICODE_CHARACTER_CLASS;

	private static final int UI = U | Pattern.CASE_INSENSITIVE;

	/**
	 * 공백으로 시작하는 정규식 앞에 붙인다: 찾기를 시작한 자리(\G)이거나 바로 앞이 공백이 아닐 때만 맞추기 시작한다.
	 * 결과는 jobfit 의 정규식과 같다. 가장 앞의 맞는 자리는 공백 줄의 한가운데일 수 없기 때문이다(한 칸 앞에서도 맞는다).
	 * 이것이 없으면 긴 공백 줄의 자리마다 공백을 끝까지 다시 읽어 공백 수의 제곱만큼 걸린다(공백 4만 칸에 몇 초).
	 */
	static final String RUN_START = "(?:\\G|(?<!\\s))";

	/** 섹션 이름(정규화) → 헤더 정규식. 앞에서부터 본다. 헤더로 보려면 줄이 짧아야 한다(HEADER_MAX_LEN). */
	private static final Map<String, Pattern> SECTION_PATTERNS = ordered(
			"모집부문", Pattern.compile("모집\\s*(부문|분야|직무|포지션)|채용\\s*(부문|분야|포지션)|포지션\\s*소개", U),
			// 스타트업 공고는 "이런 업무를 해요 / 이런 분을 찾아요 / 이런 분이면 더 좋아요 / 함께하면 이런 점들이 좋아요" 식으로 쓴다
			"주요업무", Pattern.compile("(주요|담당)\\s*업무|업무\\s*(내용|소개)|이런\\s*(일|업무)(을|를)?\\s*(해요|합니다|하게|맡)|하게\\s*될\\s*일"
					+ "|What you will do|Responsibilities", UI),
			"자격요건", Pattern.compile("자격\\s*(요건|조건)|지원\\s*(자격|요건)|필수\\s*(요건|자격|역량|조건|경험|스택|기술)|필요\\s*(역량|스택|기술|경험)"
					+ "|필요한\\s*(역량|경험)|기술\\s*스택|이런\\s*분(들)?(을|과|이)\\s*(찾|기다|함께|필요)|이런\\s*(기술|경험)(이|을)\\s*(필요|있|가진)"
					+ "|Requirements|Qualifications", UI),
			"우대사항", Pattern.compile("우대\\s*(사항|조건|요건)|이런\\s*분이면\\s*더|이런\\s*경험이\\s*있다면|있으면\\s*(더\\s*)?좋아요|더\\s*좋아요"
					+ "|Preferred|Nice to have", UI),
			"인재상", Pattern.compile("인재상|핵심\\s*가치|우리가\\s*찾는\\s*사람|함께\\s*하고\\s*싶은", U),
			"자기소개서", Pattern.compile("자기\\s*소개서|자소서\\s*(문항|항목)|서류\\s*작성\\s*(요령|가이드|팁)|지원서\\s*(문항|작성)", U),
			"전형절차", Pattern.compile("(전형|채용|선발)\\s*(절차|과정|프로세스|단계)|진행\\s*절차|채용\\s*절차|(합류|채용)\\s*여정|이렇게\\s*진행|Process", UI),
			"근무조건", Pattern.compile("근무\\s*(조건|환경|지역|형태|시간|요일|장소)|급여|연봉|고용\\s*형태|처우\\s*조건|근무할\\s*예정|이런\\s*조건에서", U),
			"복리후생", Pattern.compile("복리\\s*후생|복지|혜택|이런\\s*점(들)?이\\s*좋아요|함께하면\\s*이런|Benefits", UI),
			"회사소개", Pattern.compile("회사\\s*소개|기업\\s*소개|팀\\s*소개|우리\\s*팀은|(을|를)\\s*소개(해요|합니다)|소개해요|이런\\s*팀이에요|About", UI),
			"접수방법", Pattern.compile("접수\\s*(방법|기간|안내|마감)|지원\\s*(방법|기간)|제출\\s*서류|유의\\s*사항|유의해\\s*주세요|지원\\s*시\\s*유의"
					+ "|기타\\s*(사항|안내)|참고\\s*사항", U));

	private static final int HEADER_MAX_LEN = 30;

	/** 헤더 줄에서 키워드를 뺀 나머지 글자 수 한도. "필수 경험과 역량"은 헤더, "경력 3년 이상인 분"은 내용. */
	private static final int HEADER_EXTRA_MAX = 14;

	private static final String BULLETS = "#-•·ㆍ▪■□●○◆◇▶▷※*> ";

	/** "근무요일 : 주 5일" 같은 값 줄은 헤더가 아니다. */
	private static final Pattern KEY_VALUE = Pattern.compile("[:：]\\s*\\S", U);

	/** HtmlText 가 &lt;li&gt; 에 붙인 "- ". 구조 표시일 뿐이라 헤더 판정 전에 뗀다. */
	private static final Pattern LI_MARK = Pattern.compile("^-\\s+", U);

	/** "[근무 조건]" 같은 괄호 헤더. */
	private static final Pattern BRACKETS = Pattern.compile("^[\\[(【<]\\s*|" + RUN_START + "\\s*[\\])】>]$", U);

	/**
	 * 글머리를 뗀 줄이 이보다 길면 괄호를 볼 것도 없이 내용이다. 괄호와 공백을 떼도 머리(30자 이하)가 되지 않는다.
	 * jobfit 과 결과가 다른 것은 괄호 안쪽이나 줄 앞에 공백을 30칸 넘게 둔 줄뿐이다.
	 */
	private static final int CONTENT_LEN = 60;

	/** 회사 이름의 괄호·㈜는 길이에서 뺀다. "㈜예시컴퍼니(example)를 소개해요"가 이름 길이 때문에 헤더에서 빠지지 않게. */
	private static final Pattern NAME_DECOR = Pattern.compile("\\([^)]{1,30}\\)|㈜|\\(주\\)", U);

	/** 본문 유무 판정에 쓰는 묶음. 둘 이상이면서 자격요건이나 주요업무가 있어야 본문이다. */
	private static final Map<String, Pattern> BODY_GROUPS = ordered(
			"자격요건", Pattern.compile("자격\\s*요건|지원\\s*자격|필수\\s*(요건|자격|역량|경험|스택)|필요\\s*(스택|역량)|필요한\\s*(역량|경험)"
					+ "|이런\\s*분(들)?(을|과|이)\\s*(찾|기다|함께)|이런\\s*기술이\\s*필요|Requirements|Qualifications", UI),
			"주요업무", Pattern.compile("(주요|담당)\\s*업무|업무\\s*내용|이런\\s*(일|업무)(을|를)?\\s*(해요|합니다|하게)|하게\\s*될\\s*일", UI),
			"우대사항", Pattern.compile("우대\\s*(사항|조건)|Preferred", UI),
			"모집부문", Pattern.compile("모집\\s*(부문|분야|직무)|채용\\s*(부문|분야)", U),
			"전형절차", Pattern.compile("(전형|채용)\\s*(절차|과정|프로세스)|진행\\s*절차", U),
			"근무조건", Pattern.compile("근무\\s*(조건|지역|형태|시간|요일)|급여|연봉|고용\\s*형태", U));

	/** 묶음은 짧은 줄(헤더, "키: 값")에서만 센다. 개인정보 동의문의 긴 문장에 속지 않게(jobfit, CJ 채용 실측). */
	private static final int BODY_LINE_MAX = 80;

	/** 요약 칸(경력·학력·급여…). "경력 | 신입", "경력: 신입" 한 줄과, 사람인처럼 키 줄 다음에 값 줄이 오는 꼴 둘 다 읽는다. */
	static final List<String> SUMMARY_KEYS = List.of("경력", "학력", "급여", "연봉", "근무형태", "고용형태", "근무지역", "근무지", "근무요일",
			"근무시간", "마감일", "접수기간", "접수마감", "직급", "직책", "모집인원", "스킬", "핵심역량", "우대조건");

	private static final Pattern SUMMARY = Pattern.compile("^(" + String.join("|", SUMMARY_KEYS) + ")\\s*(?:\\||:|：)\\s*(.+)$", U);

	private static final int SUMMARY_VALUE_MAX = 60;

	/** 한 줄에 붙어 오는 요약 칸(잡코리아 meta description). 뒤에 요약 키가 오는 쉼표에서만 자른다("대학졸업(2,3년)이상"). */
	private static final Pattern SUMMARY_JOINED = Pattern
		.compile("\\s*,\\s*(?=(?:" + String.join("|", SUMMARY_KEYS) + ")\\s*(?:\\||:|：))", U);

	/** 메뉴·푸터로 보이는 짧은 줄. */
	private static final Pattern NOISE_WORDS = Pattern.compile("^(로그인|회원가입|고객센터|이용약관|개인정보\\s*처리방침|개인정보처리방침|즐겨찾기|스크랩|인쇄"
			+ "|공유|신고|닫기|홈|메뉴|검색|전체\\s*메뉴|TOP|더보기|바로가기|관심기업|지원하기|즉시지원|홈페이지\\s*지원|입사지원|기업정보|채용정보|공채속보"
			+ "|취업톡톡|헤드헌팅|HR\\s*매거진|아웃소싱|이력서|자소서|커리어|사람인|잡코리아|원티드)\\s*$", UI);

	private static final int NOISE_MAX_LEN = 14;

	private static final Pattern MANY_NL = Pattern.compile("\\n{3,}");

	private static final Pattern SITE_SUFFIX = Pattern
		.compile(RUN_START + "\\s*[-|–—:]\\s*(사람인|잡코리아|원티드|점핏|리멤버|프로그래머스|Wanted|Saramin|JobKorea)\\s*$", UI);

	private PostingSections() {
	}

	/**
	 * 섹션 하나.
	 *
	 * @param name   정규화한 이름(SECTION_PATTERNS 의 키)이나 {@link #HEAD}
	 * @param header 원문의 헤더 줄. 머리는 ""
	 */
	record Section(String name, String header, List<String> lines) {

		String text() {
			return Py.strip(String.join("\n", this.lines));
		}

	}

	static boolean hasPostingBody(String text) {
		StringBuilder shortLines = new StringBuilder();
		for (String line : Py.lines(text)) {
			if (Py.len(Py.strip(line)) <= BODY_LINE_MAX) {
				shortLines.append(line).append('\n');
			}
		}
		Set<String> found = new LinkedHashSet<>();
		BODY_GROUPS.forEach((name, pattern) -> {
			if (pattern.matcher(shortLines).find()) {
				found.add(name);
			}
		});
		return found.size() >= 2 && (found.contains("자격요건") || found.contains("주요업무"));
	}

	/** 메뉴·푸터로 보이는 짧은 줄과 연속 중복 줄을 뺀다. 본문 줄은 건드리지 않는다. */
	static String dropNoise(String text) {
		List<String> out = new ArrayList<>();
		String previous = null;
		for (String line : Py.lines(text)) {
			String s = Py.strip(line);
			if (!s.isEmpty() && Py.len(s) <= NOISE_MAX_LEN && NOISE_WORDS.matcher(s).find()) {
				continue;
			}
			if (!s.isEmpty() && s.equals(previous)) {
				continue;
			}
			out.add(line);
			previous = s.isEmpty() ? previous : s;
		}
		return Py.strip(MANY_NL.matcher(String.join("\n", out)).replaceAll("\n\n"));
	}

	static List<Section> split(String text) {
		List<Section> sections = new ArrayList<>();
		sections.add(new Section(HEAD, "", new ArrayList<>()));
		for (String line : Py.lines(text)) {
			String raw = LI_MARK.matcher(Py.strip(Py.strip(Py.strip(line), "| "))).replaceFirst("");
			String unbulleted = Py.lstrip(raw, BULLETS);
			String core = (Py.len(unbulleted) > CONTENT_LEN) ? "" : Py.strip(BRACKETS.matcher(unbulleted).replaceAll(""));
			String name = headerName(raw, core);
			if (name != null) {
				sections.add(new Section(name, core, new ArrayList<>()));
			}
			else {
				sections.getLast().lines().add(Py.strip(line));
			}
		}
		return sections.stream().filter((s) -> !s.text().isEmpty() || !HEAD.equals(s.name())).toList();
	}

	/** 짧고, 글머리로 시작하지 않고, 값(:)이 붙지 않은 줄이 섹션 키워드와 맞으면 헤더다. */
	private static String headerName(String raw, String core) {
		if (core.isEmpty() || Py.len(core) > HEADER_MAX_LEN) {
			return null;
		}
		if ("ㆍ•·-▪*".indexOf(raw.charAt(0)) >= 0) {
			return null;
		}
		if (KEY_VALUE.matcher(core).find()) {
			return null;
		}
		String bare = NAME_DECOR.matcher(core).replaceAll("");
		for (Map.Entry<String, Pattern> section : SECTION_PATTERNS.entrySet()) {
			Matcher m = section.getValue().matcher(bare);
			if (m.find() && Py.len(bare) - bare.codePointCount(m.start(), m.end()) <= HEADER_EXTRA_MAX) {
				return section.getKey();
			}
		}
		return null;
	}

	static Map<String, String> summaryFields(String text) {
		Map<String, String> fields = new LinkedHashMap<>();
		List<String> lines = Py.lines(text).stream().map((line) -> Py.strip(Py.strip(Py.strip(line), "| "))).toList();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			Matcher m = SUMMARY.matcher(line);
			if (m.find()) {
				String key = m.group(1);
				String value = Py.strip(m.group(2), " |");
				if (!fields.containsKey(key) && !value.isEmpty() && Py.len(value) <= SUMMARY_VALUE_MAX) {
					fields.put(key, value);
				}
				continue;
			}
			if (SUMMARY_KEYS.contains(line) && !fields.containsKey(line)) {
				// 키 줄 → (빈 줄) → 값 줄. 다음 키가 먼저 나오거나 값이 길면 요약 칸이 아니다
				for (String value : lines.subList(i + 1, Math.min(i + 4, lines.size()))) {
					if (value.isEmpty()) {
						continue;
					}
					if (!SUMMARY_KEYS.contains(value) && Py.len(value) <= SUMMARY_VALUE_MAX) {
						fields.put(line, value);
					}
					break;
				}
			}
		}
		return fields;
	}

	/** 한 줄에 붙어 오는 요약 칸을 읽는다. 잡코리아는 화면이 아니라 meta description 에만 경력·학력을 둔다. */
	static Map<String, String> summaryFromJoined(String line) {
		return summaryFields(String.join("\n", SUMMARY_JOINED.split(line, -1)));
	}

	/** 페이지 제목 끝의 사이트 이름을 뗀다. "… - 사람인", "… | 잡코리아" */
	static String cleanTitle(String title) {
		return Py.strip(SITE_SUFFIX.matcher(title).replaceAll(""));
	}

	private static Map<String, Pattern> ordered(Object... nameAndPattern) {
		Map<String, Pattern> map = new LinkedHashMap<>();
		for (int i = 0; i < nameAndPattern.length; i += 2) {
			map.put((String) nameAndPattern[i], (Pattern) nameAndPattern[i + 1]);
		}
		return map;
	}

}
