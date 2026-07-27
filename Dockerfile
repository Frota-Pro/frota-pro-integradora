FROM eclipse-temurin:21-jdk-alpine

# DANFE/PDF rendering uses JasperReports/java-danfe templates that reference
# Verdana. The Alpine JRE image does not include Microsoft Core Fonts by
# default, so PDF generation fails with JRFontNotFoundException unless the
# fonts are installed and registered in fontconfig.
RUN apk add --no-cache msttcorefonts-installer     && update-ms-fonts     && fc-cache -f

WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
