# 레이어 분리. 의존성이 그대로면 애플리케이션 레이어만 재전송
FROM eclipse-temurin:25-jre-alpine AS extractor
WORKDIR /src
COPY build/libs/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=extractor --chown=app:app /extracted/dependencies/ ./
COPY --from=extractor --chown=app:app /extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=app:app /extracted/application/ ./
USER app
EXPOSE 8080
# 컨테이너 메모리 한도 기준 힙 산정
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
