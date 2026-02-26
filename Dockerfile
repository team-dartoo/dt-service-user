FROM bellsoft/liberica-openjdk-alpine-musl:21 AS builder

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

# JAR 파일을 컨테이너로 복사
COPY build/libs/*.jar app.jar

# 소유권 변경
RUN chown -R spring:spring /app

# Non-root 사용자로 전환
USER spring

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행 (JVM 최적화 옵션 포함)
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
