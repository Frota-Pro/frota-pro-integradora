FROM eclipse-temurin:21-jdk-alpine

# DANFE/PDF rendering uses JasperReports/java-danfe templates that reference
# Verdana. The Alpine JRE image does not include Microsoft Core Fonts by
# default, so PDF generation fails with JRFontNotFoundException unless the
# fonts are installed and registered in fontconfig.
RUN apk add --no-cache msttcorefonts-installer     && update-ms-fonts     && fc-cache -f

# Fixa o fuso horário do container/JVM em America/Recife (mesmo motivo do
# Dockerfile da frota-pro-api: evita que datas calculadas com "agora" no
# container fiquem um dia à frente do horário do Brasil).
RUN apk add --no-cache tzdata
ENV TZ=America/Recife
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=America/Recife"

WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
