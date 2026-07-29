FROM maven:3.9-eclipse-temurin-21 AS build

ARG SERVICE_NAME
WORKDIR /workspace

COPY . .

RUN mvn -pl ${SERVICE_NAME} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine

ARG SERVICE_NAME
WORKDIR /app

COPY --from=build /workspace/${SERVICE_NAME}/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
