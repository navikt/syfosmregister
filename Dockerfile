FROM navikt/java:11
COPY config-preprod.json .
COPY config-prod.json .
COPY build/libs/syfosmregister-*-all.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV APPLICATION_PROFILE="remote"
