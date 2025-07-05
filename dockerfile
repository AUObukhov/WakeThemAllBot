FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/wake-them-all-bot-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]