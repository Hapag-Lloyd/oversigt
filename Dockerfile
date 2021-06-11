FROM adoptopenjdk:8-jre
RUN mkdir /opt/oversigt
WORKDIR /opt/oversigt
COPY . .
EXPOSE 80
ENTRYPOINT ["java", "-cp", "data/:res/:bin/oversigt-core-0.7-SNAPSHOT-application.jar", "com.hlag.oversigt.core.Oversigt"]
