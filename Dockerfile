FROM navikt/java:10
COPY build/libs/syfosmregister-*-all.jar app.jar
# TODO: Templating?
COPY config/preprod/application.json application-preprod.json
COPY config/prod/application.json application-prod.json
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV APPLICATION_PROFILE="remote"
