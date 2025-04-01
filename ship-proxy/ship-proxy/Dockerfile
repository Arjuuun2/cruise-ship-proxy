FROM openjdk:11-jre-slim

WORKDIR /app

COPY target/ship-proxy-0.0.1-SNAPSHOT.jar /app/ship-proxy.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ship-proxy.jar"]