FROM amazoncorretto:21-alpine

WORKDIR /app

COPY target/project9-app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
