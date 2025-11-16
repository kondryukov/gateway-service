FROM openjdk:25-ea-11-jdk-bookworm
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8787
ENTRYPOINT ["java","-jar","/app.jar"]