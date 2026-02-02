# Используем официальный образ Java 17
FROM eclipse-temurin:17-jdk-alpine

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем файлы сборки
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY src src

# Даем права на выполнение gradlew
RUN chmod +x gradlew

# Собираем проект
RUN ./gradlew build --no-daemon

# Копируем собранный JAR
RUN cp build/libs/*.jar app.jar

# Открываем порт
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]