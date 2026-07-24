# JVM 与 Native HTTP 基准

这个基准在同一台机器上，用相同的 `/hello` 接口比较 Spring Boot JVM
进程与 GraalVM Native Image。

## 准备

需要：

- GraalVM JDK 25
- `curl`
- `wrk`

先构建两个最新产物：

```bash
./gradlew bootJar nativeCompile
```

## 运行

```bash
./benchmark/run-comparison.sh
```

结果默认写入 `/tmp/graalvm-native-benchmark`：

- `startup.tsv`：5 轮交错启动，从创建进程到 `/hello` 返回 2xx
- `throughput.tsv`：预热后 3 轮吞吐、延迟和 RSS
- `artifacts.tsv`：JAR 与 Native 可执行文件大小
- `*-throughput-*.txt`：每轮原始 `wrk` 输出

可以指定输出目录并覆盖默认参数：

```bash
STARTUP_ROUNDS=7 \
BENCHMARK_TRIALS=5 \
WARMUP_DURATION=10s \
MEASURED_DURATION=20s \
THREADS=4 \
CONCURRENCY=32 \
./benchmark/run-comparison.sh /tmp/my-native-benchmark
```

脚本也支持通过 `WRK_BIN=/path/to/wrk` 指定 `wrk`。

## 如何解读

这是一个本机、单端点的相对实验，不是通用性能排名。客户端和服务端共享
CPU，WSL2 调度、后台负载、JVM 参数、GC、业务逻辑与预热时间都会改变结果。
应该查看多轮结果和中位数，而不是挑选最好的一轮。

这里测量的是“新进程启动”，不是清除操作系统页缓存后的磁盘冷启动。JAR
大小也不等于 JVM 部署总大小，因为运行 JAR 还需要 JRE。
