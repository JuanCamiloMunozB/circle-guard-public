FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY services/circleguard-promotion-service/ services/circleguard-promotion-service/

RUN chmod +x gradlew && ./gradlew :services:circleguard-promotion-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /workspace/services/circleguard-promotion-service/build/libs/*.jar app.jar
RUN chown spring:spring /app/app.jar
USER spring
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
