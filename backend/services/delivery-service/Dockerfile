FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY event-ingestion-service/pom.xml event-ingestion-service/pom.xml
COPY webhook-management-service/pom.xml webhook-management-service/pom.xml
COPY delivery-service/pom.xml delivery-service/pom.xml
COPY scheduler-service/pom.xml scheduler-service/pom.xml
COPY delivery-service/src delivery-service/src

RUN mvn -pl delivery-service -am -DskipTests package spring-boot:repackage

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/delivery-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
