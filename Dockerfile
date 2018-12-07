FROM navikt/java:10
COPY build/libs/syfosmregister-*-all.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV APPLICATION_PROFILE="remote"
