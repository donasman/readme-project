package com.readme;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 서버 전체를 띄우는 테스트에 붙인다. DB 는 Testcontainers 의 PostgreSQL 이고, Flyway 가 마이그레이션을 모두 적용한다.
 *
 * <ul>
 * <li>프로필은 test 다(application-test.yml). dev 의 설정, 예시 데이터, 임시 로그인은 없다.</li>
 * <li>시계는 2026-11-13 00:30(서울)에 멈춰 있다({@link FixedClockConfiguration}).</li>
 * <li>데이터는 주입받은 {@link Fixtures} 로 넣고, 요청은 주입받은 MockMvcTester 로 보낸다.
 * 로그인한 요청은 {@link TestRequests#loginAs}, 바꾸는 요청에는 {@link TestRequests#xsrf} 를 붙인다.</li>
 * <li>메일은 보내지 않고 주입받은 {@link TestMailbox} 에 모인다.</li>
 * </ul>
 *
 * <p>이 표시만 붙인 테스트들은 같은 Spring 설정을 쓰므로 서버와 DB 를 한 번만 띄워 나눠 쓴다.
 * 테스트 클래스에 설정(@ActiveProfiles, @Import, @MockitoBean 등)을 더하면 그 클래스만 따로 띄우게 되니, 꼭 필요할 때만 더한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class, Fixtures.class, TestMailbox.class })
public @interface IntegrationTest {
}
