# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY microservices/herramientas-service/pom.xml microservices/herramientas-service/pom.xml
COPY microservices/cotizaciones-service/pom.xml microservices/cotizaciones-service/pom.xml
COPY microservices/herramientas-service/src microservices/herramientas-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl microservices/herramientas-service -am verify

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S jardinrent && adduser -S jardinrent -G jardinrent
WORKDIR /app
COPY --from=build /workspace/microservices/herramientas-service/target/app.jar app.jar
USER jardinrent
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
