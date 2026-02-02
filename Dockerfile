# Этап сборки
FROM gradle:7.6-jdk17-alpine AS builder

WORKDIR /app

# Копируем только нужные для сборки файлы
COPY gradlew .
COPY gradle gradle/
COPY build.gradle .
COPY settings.gradle.kts .
COPY src src/

# Даем права и собираем
RUN chmod +x gradlew
RUN ./gradlew build -x test --no-daemon

# Этап запуска
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Копируем собранный JAR
COPY --from=builder /app/build/libs/*.jar app.jar

# Открываем порт
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]