# syntax=docker/dockerfile:1
FROM eclipse-temurin:24-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon dependencies
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test \
 && JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
 && test -n "$JAR_FILE" \
 && cp "$JAR_FILE" /app.jar

FROM eclipse-temurin:24-jre
RUN useradd --system --uid 10001 --home-dir /app app \
 && mkdir -p /app /data/photos \
 && chown -R app:app /app /data
USER app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    PHOTO_STORAGE_ROOT=/data/photos \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
VOLUME ["/data/photos"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
