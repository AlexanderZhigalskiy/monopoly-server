FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew build -x test --no-daemon
RUN find . -name "*.jar" -type f | head -1 | xargs -I {} cp {} app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]