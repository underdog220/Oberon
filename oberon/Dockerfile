FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="DevLoop" \
      description="Oberon — Zentrale KI-Plattform"

RUN mkdir -p /data && adduser -D -h /app oberon
WORKDIR /app

COPY build/libs/oberon-all.jar /app/oberon.jar

USER oberon

EXPOSE 17900

ENV OBERON_PORT=17900 \
    OBERON_HOST=0.0.0.0 \
    OBERON_DATA_DIR=/data \
    OBERON_DOMAINS=SYSTEM,GUTACHTEN \
    OBERON_TOKEN=changeme

VOLUME /data

ENTRYPOINT ["java", "-jar", "/app/oberon.jar"]
