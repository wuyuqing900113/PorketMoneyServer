# syntax=docker/dockerfile:1
# ============================================================
# PorketMoneyServer 生产镜像（M7 设计 §4，D56）
# 多阶段构建：builder 跑全质量门禁，runtime 仅含 JRE + 分层 jar
# 构建上下文：仓库根目录（.dockerignore 已瘦身）
# ============================================================

# ---------- Stage 1：构建（含全质量门禁） ----------
FROM maven:3.9.16-eclipse-temurin-25 AS builder
WORKDIR /build

# 先拷贝 pom 预拉依赖，利用 Docker 层缓存（源码变更不重拉依赖）
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# 拷贝源码与质量配置，执行全量构建 + 质量门禁
# （单测/集成测试 + Checkstyle/PMD/SpotBugs 0 违规 + JaCoCo ≥80% BUNDLE）
COPY src ./src
COPY config ./config
RUN mvn -B -ntp clean verify

# 拆分 Spring Boot 分层 jar：dependencies / spring-boot-loader /
# snapshot-dependencies / application，供 runtime 分层拷贝（D56 分层镜像）。
# Boot 4：layertools jarmode 已更名 tools（工件 spring-boot-jarmode-tools）；
# 需 --launcher 输出可被 JarLauncher 启动的展开布局，并以 --layers 显式指定按层
# 分目录（默认 extract 为 app.jar + lib/ 扁平布局，不按层分目录，无法分层 COPY）。
RUN java -Djarmode=tools -jar target/PorketMoneyServer-*.jar extract --launcher \
        --layers dependencies,spring-boot-loader,snapshot-dependencies,application \
        --destination target/extracted

# ---------- Stage 2：运行时 ----------
FROM eclipse-temurin:25-jre

# 健康检查依赖 curl（temurin JRE 基础镜像默认不含），root 阶段安装
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 运行（mission.md 安全红线：零硬编码/最小权限）
RUN groupadd -r pocket && useradd -r -g pocket pocket
WORKDIR /app

# 按分层拷贝：依赖层变更频率最低，置于最前以最大化镜像缓存命中
COPY --from=builder /build/target/extracted/dependencies/ ./
COPY --from=builder /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/target/extracted/application/ ./

# 日志目录（GC 日志 + 应用日志），归属非 root 用户
RUN mkdir -p /app/logs && chown -R pocket:pocket /app
USER pocket

# JVM 参数（M3 D22 ZGC / D23 堆内存）：
#   ZGC 亚毫秒停顿（匹配 P95 ≤ 500ms）+ 容器感知堆百分比 + OOM 即退出
#   + GC 日志落盘供 SLS 采集（M7 §8.2）
ENV JAVA_OPTS="-XX:+UseZGC \
    -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Xlog:gc=info,gc+heap=info:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m"

EXPOSE 8080

# 分层 jar 启动入口（Spring Boot 4 loader 包名）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

# 容器健康检查（对齐 application.yml 已启用的 health probes）
# start-period 给 Flyway 迁移 + Spring 上下文启动留足窗口
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
