FROM openjdk:11-jre-slim

WORKDIR /app

COPY target/offshore-proxy-0.0.1-SNAPSHOT.jar /app/offshore-proxy.jar

EXPOSE 8081 9090

ENTRYPOINT ["java", "-jar", "offshore-proxy.jar"]