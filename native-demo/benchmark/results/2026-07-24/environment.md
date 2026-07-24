# 测量环境

- 日期：2026-07-24
- 系统：Linux 5.15.167.4-microsoft-standard-WSL2 x86_64
- CPU：AMD Ryzen 7 4800H，8 核 16 线程
- 分配内存：7.5 GiB
- Java：GraalVM CE JDK 25.0.2
- Native Image：GraalVM CE 25.0.2，Serial GC
- Spring Boot：4.1.0
- `wrk`：Debian 4.1.0-4build2
- 端点：`GET /hello`
- 启动轮数：每种运行时 5 轮，JVM 与 Native 交错执行
- 预热：每种运行时 5 秒
- 测量：每种运行时 3 轮，每轮 10 秒，4 threads / 32 connections

构建 Native Image 用时 5 分 2 秒，完整 Gradle 构建用时 5 分 47 秒，
构建峰值 RSS 为 4.33 GiB。
