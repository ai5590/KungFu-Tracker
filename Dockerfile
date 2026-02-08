FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 5000
ENV APP_DATA_DIR=/app/data
ENV APP_USERS_FILE=/app/data/users.txt
VOLUME /app/data
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=5000", "--app.data-dir=/app/data", "--app.users-file=/app/data/users.txt"]
