# Используем базовый образ с JDK 17
FROM eclipse-temurin:17-jdk-focal AS builder

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем Gradle wrapper и файлы сборки
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

# Даем права и собираем
RUN chmod +x gradlew
RUN ./gradlew build --no-daemon

# Этап запуска
FROM eclipse-temurin:17-jre-focal
WORKDIR /app

# Копируем собранный JAR из предыдущего этапа
COPY --from=builder /app/build/libs/*.jar app.jar

# Экспонируем порт
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]