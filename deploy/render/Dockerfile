FROM node:22-alpine AS desktop
WORKDIR /web
COPY samuschat-desktop/package.json samuschat-desktop/package-lock.json ./
RUN npm ci
COPY samuschat-desktop/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY chatapp-backend/pom.xml ./
COPY chatapp-backend/src/main ./src/main
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache nginx gettext-envsubst tini \
    && addgroup -S -g 10001 samuschat \
    && adduser -S -u 10001 -G samuschat samuschat \
    && mkdir -p /app/uploads /app/logs \
    && chown -R samuschat:samuschat /app
WORKDIR /app
COPY --from=desktop --chown=samuschat:samuschat /web/dist /app/web
COPY --from=backend --chown=samuschat:samuschat /build/target/chatapp-backend-*.jar /app/app.jar
COPY --chown=samuschat:samuschat deploy/render/nginx.conf.template /app/nginx.conf.template
COPY --chown=samuschat:samuschat deploy/render/start.sh /app/start.sh
RUN sed -i 's/\r$//' /app/start.sh && chmod 755 /app/start.sh
USER samuschat
ENV PORT=10000 \
    SPRING_PROFILES_ACTIVE=prod,render \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -Xss512k -XX:ActiveProcessorCount=2"
EXPOSE 10000
ENTRYPOINT ["/sbin/tini", "-g", "--", "/app/start.sh"]
