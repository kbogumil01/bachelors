FROM openjdk:17
#LABEL maintainer="medicalServer"
#ADD target/paint-0.0.1.jar server-docker.jar
COPY target/dispatcherServer-0.0.1-SNAPSHOT.jar dispatcher-docker.jar
ENTRYPOINT ["java", "-jar", "dispatcher-docker.jar"]
