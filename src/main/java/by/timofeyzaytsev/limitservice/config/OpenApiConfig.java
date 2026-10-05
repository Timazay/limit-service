package by.timofeyzaytsev.limitservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Описание сервиса для Swagger UI.
 *
 * <p>Разделено на два API намеренно: интеграционный принимает транзакции от
 * банковских сервисов, клиентский обслуживает владельца счёта.</p>
 *
 * <p>Схемы безопасности здесь нет: авторизация живёт в отдельном
 * микросервисе и в этом прототипе отсутствует, объявлять её в контракте было бы
 * обещанием, которого сервис не выполняет.</p>
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Limit Service",
        version = "0.0.1",
        description = """
            Прототип микросервиса учёта расходов с месячным лимитом в USD.

            Расходы принимаются в валюте счёта и пересчитываются в USD по
            биржевому курсу закрытия за день расхода. Если закрытия за этот день
            нет (выходной или праздник), берётся последнее доступное закрытие.

            Месячные границы считаются в часовом поясе сервиса — Asia/Almaty по
            умолчанию, переопределяется переменной TIME_ZONE. Транзакции
            приходят с любым смещением, но месяц определяется по зоне сервиса.

            Если лимит не установлен, он равен 1000 USD. Категорий расхода две:
            product и service, лимиты на них считаются раздельно. Лимит нельзя
            обновить, только установить новый: дата нового лимита всегда
            проставляется сервером и не может относиться к прошлому.

            Доступ к API не разграничен: авторизация вынесена в отдельный
            микросервис и в этом прототипе не задействована.
            """,
        contact = @Contact(name = "Timofey Zaytsev"),
        license = @License(name = "Учебное задание")
    ),
    servers = @Server(url = "/", description = "Локальный запуск")
)
public class OpenApiConfig {
}