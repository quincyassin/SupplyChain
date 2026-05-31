# 后端镜像：多阶段构建，运行环境 JRE 17
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# 容器内使用国内 Maven 镜像，避免 dependency:go-offline 失败后静默继续导致 package 缺依赖
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 依赖层：仅 pom.xml 变更时才重新下载（src 变更时复用本层）
COPY pom.xml .
RUN mvn -B dependency:resolve dependency:resolve-plugins -Dmaven.test.skip=true

COPY src ./src
# 镜像构建跳过测试编译与执行，加快构建并减少 Docker 内 testCompile 失败风险
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
COPY --from=build /build/target/order-split-merge-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
