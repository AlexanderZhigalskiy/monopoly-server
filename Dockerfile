# Используем базовый образ с JDK 17
FROM eclipse-temurin:17-jdk-focal AS builder

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем файлы сборки
COPY . .

# Даем права и собираем
RUN chmod +x gradlew
RUN ./gradlew build --no-daemon

# Этап запуска
FROM eclipse-temurin:17-jre-focal
WORKDIR /app

# Копируем собранный JAR
COPY --from=builder /app/build/libs/*.jar app.jar

# Экспонируем порт
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]