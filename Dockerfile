# Сборочный этап: собираем исполняемый jar при помощи Gradle wrapper.
FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

# Сначала копируем только файлы сборки, чтобы слой с зависимостями кэшировался
# и не пересобирался при каждом изменении исходников.
COPY gradle ./gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp "$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /workspace/app.jar

# Этап запуска: только JRE и собранный jar.
FROM eclipse-temurin:17-jre

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app

EXPOSE 8080

# Дополнительные JVM-параметры можно передать через JAVA_TOOL_OPTIONS.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
