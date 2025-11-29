FROM bellsoft/liberica-openjdk-alpine-musl:21 AS builder
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x ./gradlew
RUN ./gradlew bootJar

FROM bellsoft/liberica-openjdk-alpine-musl:21

WORKDIR /app

COPY --from=builder build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]