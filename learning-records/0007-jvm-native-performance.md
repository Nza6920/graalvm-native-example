# 用数据选择 JVM 或 Native

用户完成了同一 Spring Boot 4.1.0 服务在 JVM 与 GraalVM Native Image
下的可重复对比。结果证明 Native 的核心优势是冷启动与较低 RSS，而不是所有
性能指标都必然超过 JVM。

## Evidence

- 使用 GraalVM CE JDK 25.0.2 构建了最新 Boot JAR 和 Native executable。
- Native Image 生成用时 5 分 2 秒，峰值 RSS 4.33 GiB。
- JVM 与 Native 各执行 5 轮交错启动，以 `/hello` 首次返回 2xx 为就绪条件。
- 使用 `wrk` 预热 5 秒，再各执行 3 轮 10 秒、4 threads / 32 connections 压测。
- 每一轮完整数据已保存在
  `native-demo/benchmark/results/2026-07-24/`。

## Median results

| 指标 | JVM | Native | 解读 |
| --- | ---: | ---: | --- |
| 启动到 HTTP 就绪 | 5663.677 ms | 144.847 ms | Native 快 39.09× |
| 就绪 RSS | 268588 KiB | 94624 KiB | Native 低 64.8% |
| 吞吐量 | 18597.64 req/s | 13427.63 req/s | JVM 高 38.5% |
| p50 | 1.476 ms | 2.070 ms | JVM 更低 |
| p95 | 3.744 ms | 4.901 ms | JVM 更低 |
| 压测期 RSS | 272976 KiB | 101424 KiB | Native 更低 |

## Artifact boundary

- Boot JAR：20,052,538 bytes（19.13 MiB），运行时仍需要 JRE。
- Native executable：87,558,408 bytes（83.50 MiB），不需要安装 JRE，
  但绑定目标 OS、CPU 架构和系统动态库边界。

所以不能用“JAR 文件更小”推导“JVM 部署更小”。公平比较应覆盖完整运行环境
或 OCI 镜像。

## Implications

冷启动、弹性扩缩、短任务和容器内存密度优先时，应优先评估 Native。长期常驻、
峰值吞吐、动态加载与 JIT 自适应优化更重要时，应优先评估 JVM。最终选择必须用
真实业务端点、依赖和生产参数重新测量，不能把本机的倍数当作通用承诺。
