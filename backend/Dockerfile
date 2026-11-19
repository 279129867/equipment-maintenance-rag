# 应用镜像：jar 由 CI（Jenkins）或本地 mvn package 产出，镜像只负责运行时
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="eqmaint-demo" \
      description="设备维保工单系统 + 维修知识 RAG 助手（个人练习项目，虚拟产线数据）"

WORKDIR /app

# 时区与中文字体（PDF 解析、日志时间依赖）
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && addgroup -S app && adduser -S app -G app

COPY target/eqmaint-backend-1.0.0.jar app.jar

# 上传的手册落在 /app/data/uploads，用卷挂出去，重建容器不丢知识库原文
RUN mkdir -p /app/data/uploads /app/logs && chown -R app:app /app
VOLUME ["/app/data/uploads"]

USER app
EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENV TZ=Asia/Shanghai

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
