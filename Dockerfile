# syntax=docker/dockerfile:1
# ── 빌드 단계: JDK 24 + Gradle Wrapper ─────────────────────────────
FROM eclipse-temurin:24-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# 의존성 레이어 캐시(실패해도 무시: 소스 복사 후 본 빌드에서 다시 해석)
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && mv "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n1)" /app.jar

# ── 실행 단계: JRE 만 포함, 비루트 사용자 ─────────────────────────
FROM eclipse-temurin:24-jre
RUN useradd --system --uid 10001 --home-dir /app app \
 && mkdir -p /app /data/photos && chown -R app /app /data
USER app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    PHOTO_STORAGE_ROOT=/data/photos \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
VOLUME /data/photos
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
