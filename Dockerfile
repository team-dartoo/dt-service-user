FROM bellsoft/liberica-openjdk-alpine-musl:21

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]