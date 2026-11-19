# 충남 주차 안내 백엔드 — 배포용 이미지
# 빌드: docker build -t parking-backend .
# 실행: docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod -e DB_HOST=... parking-backend

# 1단계: Gradle 로 실행 파일(jar) 만들기 (테스트는 CI·로컬에서 돌린다)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: 실행에 필요한 JRE 와 jar 만 담는다
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 app && mkdir -p /app/uploads && chown app /app/uploads
COPY --from=build /app/build/libs/*.jar app.jar
USER app
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
