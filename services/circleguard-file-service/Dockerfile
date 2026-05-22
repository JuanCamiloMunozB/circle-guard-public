FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY services/circleguard-file-service/ services/circleguard-file-service/

RUN chmod +x gradlew && ./gradlew :services:circleguard-file-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /workspace/services/circleguard-file-service/build/libs/*.jar app.jar
RUN chown spring:spring /app/app.jar
USER spring
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
