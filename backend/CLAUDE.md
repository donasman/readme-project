# 리드미 서버 작업 규칙

이 폴더(`backend/`)의 코드를 만들거나 고칠 때 따른다. 브랜치와 커밋 규칙은 루트의 `CLAUDE.md`에 있다.

## 설계 문서

- 테이블과 API 의 근거는 `../docs/리드미_설계_로그인_경험카드.md`, `../docs/리드미_설계_그룹_공고.md`다. 둘 다 초안이다.
- 초안의 가정(각 문서 2절)대로 만든다. 가정과 다르게 만들어야 하면 만들기 전에 묻는다.
- 설계와 다르게 만든 것은 그 설계 문서에 적는다. 응답 모양이 프런트 타입과 달라지면 그룹·공고 문서의 "프런트와 맞출 것"에 적는다.

## 띄우기와 확인

- JDK 21 이 필요하다. Gradle 이 PC 에 깔린 JDK 21 을 찾아 쓰므로 `JAVA_HOME`이 다른 버전이어도 된다.
- 개발 DB 와 메일 수신기: 저장소 맨 위에서 `docker compose up -d`. PostgreSQL 은 `localhost:5433`이다. 보낸 메일은 `http://localhost:8025`.
- 서버: `.\gradlew bootRun` → `http://localhost:8080/actuator/health`가 `UP`.
- 커밋 전에 `.\gradlew build`를 돌린다. 테스트가 Testcontainers 를 쓰므로 Docker 가 켜져 있어야 한다.
- Spring Boot 는 4.1.2 다. 스타터 이름을 3.x 의 기억으로 바꾸지 않는다(예: 4.x 는 `spring-boot-starter-webmvc`, 테스트는 `spring-boot-starter-<이름>-test`). 이름이 의심스러우면 Gradle 캐시의 jar 나 공식 문서로 확인한다.

## 패키지

`com.readme.<기능>`으로 나눈다. 기능 목록과 테이블은 설계 문서에 있다(로그인·경험카드는 각 `package-info.java`, 그룹·공고는 10절).

- 기능 패키지 안은 더 나누지 않는다. `…Controller`, `…Service`, `…Repository`, 엔티티, 요청·응답 record 를 한 폴더에 둔다.
- 클래스는 package-private 이 기본이다. 다른 기능이 써야 하는 것만 `public`으로 연다.
- `common`: 설정(`config`), 오류 응답(`error`). 기능 패키지를 불러오지 않는다.
- 테이블마다 주인 패키지가 있다(설계 문서의 표). 그 테이블에 쓰는 코드(insert, update, delete)는 주인 패키지에만 둔다. 다른 기능의 데이터를 바꿔야 하면 그 기능의 `public` 서비스를 부른다.
- 쓰기(행을 만들고 고치고 지우기, 그러려고 엔티티를 읽는 것까지)는 JPA 엔티티와 Spring Data 리포지토리로 한다.
- 응답을 만드는 조회는 `JdbcClient`로 SQL 을 쓴다. 목록·상세처럼 여러 테이블을 묶는 조회는 테이블을 넘나들어도 된다(설계 문서 3.3 에서 확인한 SQL 을 옮긴다).

## API

- 주소는 `/api` 아래, 본문은 JSON 이다. 메서드는 GET, POST, PUT, DELETE 만 쓴다(CORS 가 이 넷만 받는다).
- 로그인은 서버 세션 쿠키다. JWT 를 넣지 않는다. CSRF 를 끄지 않는다.
- 로그인 없이 여는 주소(`SecurityConfig`의 `permitAll`)를 더할 때는 먼저 묻는다.
- 요청·응답은 record 로 만든다. 응답 모양은 프런트의 `types.ts`(`frontend/src/features/<기능>/`, `frontend/src/domain/<이름>/`)와 맞춘다.
- id 는 숫자(`Long`)다.
- 입력은 `@Valid`와 제약(`@NotBlank`, `@Size` 등)으로 검사한다. 길이 제한은 테이블 칸의 길이와 같게 한다.

## 응답에 싣는 값

- 남에게 보이면 안 되는 값(공개 범위 밖의 지원 상태 등)은 서버가 잘라서 보낸다. 프런트가 숨기기를 기대하고 보내지 않는다.
- DB 에는 영문 대문자 값(`APPLIED`, `INTERVIEW_1`)을 둔다. 화면이 쓰는 값(`applied`, `"1차 면접 단계"`)으로는 서버가 응답을 만들 때 바꾼다.

## 오류

- 응답은 언제나 `{ "code": "...", "message": "..." }`다. 컨트롤러 쪽은 `common/error/GlobalExceptionHandler`가, 보안 필터가 막은 요청(401, CSRF 403)은 `SecurityConfig`가 이 꼴로 쓴다.
- 정해진 오류는 `throw new ApiException(GroupErrorCode.GROUP_NOT_FOUND)`처럼 던진다.
- 기능의 오류 코드는 그 기능 패키지에 `ErrorCode`를 구현한 enum 으로 둔다(예: `group/GroupErrorCode`). 상수 이름이 `code`가 된다. 기능에 속하지 않는 것만 `CommonErrorCode`에 둔다.
- 코드를 새로 만들면 HTTP 상태와 함께 설계 문서의 API 절에 적는다. 프런트가 처리해야 한다. 공통 코드 목록은 그룹·공고 문서 4절 머리에 있다.
- 메시지는 화면에 그대로 보일 수 있으므로 사용자에게 하는 말로 쓴다. 예외의 내부 내용을 응답에 싣지 않는다.

## 날짜와 시각

- 한국 날짜(오늘, 마감됨, `postedAt`)는 `Clock` 빈(`common/config/TimeConfig`, `Asia/Seoul`)을 주입받아 `LocalDate.now(clock)`으로 구한다. 시계 없이 `LocalDate.now()`를 부르지 않는다.
- DB 의 `timestamptz`는 `Instant`로 받는다. 응답에서 날짜는 `"YYYY-MM-DD"`, 시각은 `+09:00`을 붙인 ISO 8601 이다(그룹·공고 문서 5.4).

## DB

- 스키마는 Flyway 로만 바꾼다(`src/main/resources/db/migration/V{번호}__{이름}.sql`). 새 파일은 V1 처럼 `set search_path to readme;`로 시작한다.
- `V…` 마이그레이션은 운영에도 적용된다. 개발용 사용자나 예시 데이터를 넣지 않는다.
- JPA 는 `ddl-auto: validate`다. 엔티티가 테이블과 다르면 서버가 뜨지 않는다.
- 토큰(가입 확인, 비밀번호 재설정, 초대)은 SHA-256 해시로만 보관한다. 원문은 메일이나 링크에만 있다.
- Supabase 전용 기능(Auth, Storage, RLS)에 기대지 않는다. 다른 PostgreSQL 로도 옮길 수 있어야 한다.
- 연결 풀 크기 5(`spring.datasource.hikari.maximum-pool-size`)는 Supabase 무료 구간에 맞춘 값이다. 늘리려면 먼저 묻는다.

## 설정과 비밀 값

- 내 PC 에서만 쓰는 값(구글 클라이언트 비밀, Claude API 키 등)은 `backend/application-local.yml`에 둔다. git 에 올라가지 않는다.
- 운영 값은 환경 변수로 받는다. 환경 변수를 더하면 `backend/.env.example`과 `application-prod.yml`에 같이 적는다.

## jobfit 에서 옮기기

- pytest 테스트를 JUnit 으로 먼저 옮기고, 그 테스트가 통과할 때까지 구현한다.
- CLI, 엑셀 입출력, 로컬 파일로 하는 상태 관리는 옮기지 않는다.

## 테스트

- DB 가 필요한 테스트는 PostgreSQL(Testcontainers)로 돌린다. H2 로 바꾸지 않는다.
- 규칙만 있는 코드(상태 글자, 공개 범위 자르기, 댓글 링크 나누기, 공고키, 카드 검사)는 Spring 없이 JUnit 으로 테스트한다.
- API 와 DB 를 거치는 테스트는 `@IntegrationTest`를 붙이고 주입받은 `MockMvcTester`로 요청한다. 이 표시를 붙인 테스트는 서버와 DB 를 한 번만 띄워 나눠 쓴다. 테스트 클래스에 설정(`@Import`, `@MockitoBean`, `@TestPropertySource`)을 더하면 그 클래스만 따로 띄우게 되니 꼭 필요할 때만 더한다.
- 테스트끼리 DB 를 나눠 쓰므로 각 테스트가 자기 데이터를 만들고, 다른 테스트가 만든 데이터에 기대지 않는다.
- 바꾸는 요청에는 `TestRequests.xsrf()`를 붙인다. spring-security-test 의 `csrf()`는 쓰지 않는다. 처음 부를 때 CSRF 토큰 저장소를 세션 방식으로 바꿔 끼워, 같은 서버를 쓰는 뒤의 테스트까지 달라진다.

## 아직 정하지 않은 것

여기 있는 것은 임의로 정하지 않는다. 걸리면 선택지를 내고 묻는다. 정해지면 위의 절로 옮긴다.

- 이미 들어간 마이그레이션을 고쳐도 되는 시점. 고치게 되면 Flyway 가 바뀐 파일을 거부하므로 로컬 DB 를 `docker compose down -v`로 지우고 다시 띄워야 한다.
- 테스트를 어디까지 둘지(어떤 코드에 어느 정도로).
