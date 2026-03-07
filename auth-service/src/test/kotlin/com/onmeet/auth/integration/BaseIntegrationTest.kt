package com.onmeet.auth.integration

import org.junit.jupiter.api.BeforeAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * 모든 통합 테스트의 기반 클래스.
 * Testcontainers를 사용하여 MySQL 9.0과 Redis 7.0 컨테이너를 자동으로 시작합니다.
 * Flyway 마이그레이션이 자동으로 실행되어 DB 스키마가 준비됩니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
abstract class BaseIntegrationTest {

    companion object {
        /**
         * MySQL 9.0 컨테이너.
         * auth_db 데이터베이스를 생성하고 Flyway 마이그레이션을 실행합니다.
         */
        @Container
        @JvmStatic
        val mysqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:9.0"))
            .withDatabaseName("auth_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true) // 테스트 간 컨테이너 재사용으로 시작 시간 단축

        /**
         * Redis 7.0 컨테이너.
         * JWT 토큰 블랙리스트 및 세션 관리용.
         */
        @Container
        @JvmStatic
        val redisContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true)

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            mysqlContainer.start()
            redisContainer.start()
        }

        /**
         * Spring 애플리케이션 컨텍스트에 동적으로 프로퍼티를 주입합니다.
         * Testcontainers가 할당한 랜덤 포트를 사용하여 DB와 Redis에 연결합니다.
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            // MySQL 연결 설정
            registry.add("spring.datasource.url") { mysqlContainer.jdbcUrl }
            registry.add("spring.datasource.username") { mysqlContainer.username }
            registry.add("spring.datasource.password") { mysqlContainer.password }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }

            // Redis 연결 설정
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379).toString() }

            // Flyway 설정 (자동 마이그레이션 활성화)
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.flyway.baseline-on-migrate") { "true" }
        }
    }
}
