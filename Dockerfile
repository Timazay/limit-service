# Сборка и запуск в одном образе: в финальный слой попадает только jar,
# без Maven, JDK и исходников.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Слой зависимостей меняется реже, чем исходники, поэтому копируется
# отдельно и переиспользуется Docker'ом при правке кода.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src/ src/

# package тянет только surefire: интеграционные тесты живут в фазе verify
# и требуют Docker, недоступный внутри сборки образа.
RUN mvn -B -q package

FROM eclipse-temurin:21-jre-alpine AS runtime

# Приложение работает не под root: без этого любой шелл в контейнере даёт
# права администратора.
RUN addgroup -S app && adduser -S app -G app

# TZ нужен и JVM: created_at в сущностях проставляется @PrePersist через
# OffsetDateTime.now(), то есть по системной зоне, а не через бин Clock.
RUN apk add --no-cache tzdata
ENV TZ=Asia/Almaty

WORKDIR /app
COPY --from=build /workspace/target/limit-service-*.jar app.jar

USER app
EXPOSE 8080

# Флаги для контейнера вместо фиксированных значений: лимит heap у JVM по
# умолчанию ориентирован на хост, а в контейнере это неверно.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]

# Swagger и лимиты поднимаются вместе с контекстом, поэтому проверяем API,
# а не «процесс жив»: неработающий контроллер тоже оставил бы процесс живым.
# start-period нужен, потому что Liquibase накатывает схему при старте.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget -qO- "http://localhost:8080/api/v1/limits?accountFrom=0000000000" > /dev/null || exit 1