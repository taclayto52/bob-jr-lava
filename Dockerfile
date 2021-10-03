FROM debian:stable

ARG PROJECT_VERSION
ADD build/libs/bob-jr-lava-${PROJECT_VERSION}-all.jar /opt/bob-jr/bob-jr-lava-all.jar

# install openJDK 14
RUN apt-get update
RUN apt-get install -y wget apt-transport-https gnupg
RUN wget https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public
RUN gpg --no-default-keyring --keyring ./adoptopenjdk-keyring.gpg --import public
RUN gpg --no-default-keyring --keyring ./adoptopenjdk-keyring.gpg --export --output adoptopenjdk-archive-keyring.gpg
RUN rm adoptopenjdk-keyring.gpg
RUN mv adoptopenjdk-archive-keyring.gpg /usr/share/keyrings
RUN echo "deb [signed-by=/usr/share/keyrings/adoptopenjdk-archive-keyring.gpg] https://adoptopenjdk.jfrog.io/adoptopenjdk/deb buster main" | tee /etc/apt/sources.list.d/adoptopenjdk.list
RUN apt-get update
RUN apt-get install -y adoptopenjdk-14-hotspot

ENTRYPOINT java -jar /opt/bob-jr/bob-jr-lava-all.jar