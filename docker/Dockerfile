# === 阶段 1: 运行 ===
FROM library/eclipse-temurin:21-jdk-jammy

LABEL maintainer="my-panel"
LABEL description="My-Panel Admin/Proxy"

WORKDIR /my-panel
ENV TZ=Asia/Shanghai

# 复制两个 jar
COPY my-panel-admin.jar /my-panel/admin/my-panel-admin.jar
COPY my-panel-proxy.jar /my-panel/proxy/my-panel-proxy.jar

# 暴露端口:8888(admin web)、9876(proxy)
EXPOSE 8888 9876

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
# APP_JAR 环境变量决定运行哪个 jar,默认 admin
ENV APP_JAR=admin/my-panel-admin.jar
# CONFIG_ARGS 由 docker-compose 注入,指定外部配置文件路径
#   admin:  --spring.config.additional-location=file:/my-panel/admin/config/
#   proxy:  --spring.config.additional-location=file:/my-panel/proxy/config/
ENV CONFIG_ARGS=""

# admin 健康检查(Actuator /actuator/health 端点)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /my-panel/$APP_JAR $CONFIG_ARGS"]
