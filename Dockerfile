FROM eclipse-temurin:17

ARG PROJECT_VERSION
ARG DATA_DOG_API_KEY
COPY build/libs/bob-jr-lava-${PROJECT_VERSION}-all.jar /opt/bob-jr/bob-jr-lava-all.jar
# change this pattern to instead target a file guranteed to exist and then additional targets using *
COPY build/resources/main/soundFiles/readme.txt build/resources/main/soundFiles/*.webm build/resources/main/soundFiles/*.opus build/resources/main/soundFiles/percussion/*.mp3 /opt/bob-jr/soundFiles/

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

ENV DD_AGENT_MAJOR_VERSION=7
ENV DD_API_KEY=${DATA_DOG_API_KEY}
ENV DD_SITE="datadoghq.com"
RUN curl -L https://s3.amazonaws.com/dd-agent/scripts/install_script.sh

CMD ["java", "-jar", "/opt/bob-jr/bob-jr-lava-all.jar"]