FROM gradle:8.5-jdk17-alpine AS build
WORKDIR /app
COPY build.gradle .
COPY settings.gradle .
COPY eureka-server/build.gradle eureka-server/build.gradle
COPY eureka-server/src eureka-server/src
RUN gradle :eureka-server:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /app/eureka-server/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
