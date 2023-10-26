FROM eclipse-temurin:17

ARG PROJECT_VERSION
COPY build/libs/bob-jr-lava-${PROJECT_VERSION}-all.jar /opt/bob-jr/bob-jr-lava-all.jar
# change this pattern to instead target a file guranteed to exist and then additional targets using *
COPY build/resources/main/soundFiles/readme.txt build/resources/main/soundFiles/*.webm build/resources/main/soundFiles/*.opus /opt/bob-jr/soundFiles/
COPY build/resources/main/soundFiles/percussion/readme.txt build/resources/main/soundFiles/percussion/*.mp3

# install openJDK 14
RUN apt-get update
RUN apt-get install -y wget apt-transport-https gnupg curl
#RUN wget https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public
#RUN gpg --no-default-keyring --keyring ./adoptopenjdk-keyring.gpg --import public
#RUN gpg --no-default-keyring --keyring ./adoptopenjdk-keyring.gpg --export --output adoptopenjdk-archive-keyring.gpg
#RUN rm adoptopenjdk-keyring.gpg
#RUN mv adoptopenjdk-archive-keyring.gpg /usr/share/keyrings
#RUN echo "deb [signed-by=/usr/share/keyrings/adoptopenjdk-archive-keyring.gpg] https://adoptopenjdk.jfrog.io/adoptopenjdk/deb buster main" | tee /etc/apt/sources.list.d/adoptopenjdk.list
#RUN apt-get update
#RUN apt-get install -y adoptopenjdk-14-hotspot

EXPOSE 8080/tcp

CMD ["java", "-jar", "/opt/bob-jr/bob-jr-lava-all.jar"]