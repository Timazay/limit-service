package by.timofeyzaytsev.limitservice.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Общая база для тестов, которым нужна настоящая PostgreSQL.
 *
 * <p>Контейнер статический и объявлен здесь, поэтому один поднимается на весь
 * прогон интеграционных тестов, а Spring переиспользует один
 * {@code ApplicationContext}. Схему накатывает Liquibase при старте контекста —
 * свою базу в тестах создавать не нужно.</p>
 *
 * <p>Суффикс {@code IT} — файл попадает в фазу {@code verify}, а не
 * {@code test}, поэтому Docker нужен только там.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");
}