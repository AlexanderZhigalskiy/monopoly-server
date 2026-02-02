# Этап сборки
FROM gradle:7.6-jdk17-alpine AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# Этап запуска
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]