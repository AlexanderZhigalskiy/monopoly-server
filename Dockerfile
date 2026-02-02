FROM openjdk:17-jdk-slim

WORKDIR /app

# Копируем собранный JAR
COPY build/libs/monopoly-server-1.0.0.jar app.jar

# Экспортируем порт
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]