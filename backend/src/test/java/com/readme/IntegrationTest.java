package com.readme;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * 서버 전체를 띄우는 테스트에 붙인다. DB 는 Testcontainers 의 PostgreSQL 이고, Flyway 가 마이그레이션을 모두 적용한다.
 *
 * <p>이 표시만 붙인 테스트들은 같은 Spring 설정을 쓰므로 서버와 DB 를 한 번만 띄워 나눠 쓴다.
 * 테스트 클래스에 설정(@Import, @MockitoBean 등)을 더하면 그 클래스만 따로 띄우게 되니, 꼭 필요할 때만 더한다.
 * 요청은 주입받은 MockMvcTester 로 보낸다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
