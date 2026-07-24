# 教学备注

- 使用中文授课。
- 技术基线：Spring Boot 4.x、Gradle、JDK 25。
- 偏好快速进入可操作内容；先给结论，必要的兼容性风险简短说明。
- 每课聚焦一个小目标，并提供可立即执行的检查或练习。

## 当前进度

- [x] 第 1 课：使用 Buildpacks 构建 Native OCI 镜像，并与普通 JVM 镜像对比。
- [x] 第 2 课：使用 `nativeCompile` 构建本地原生可执行文件，并运行 `nativeTest`。
- [x] 第 3 课：制造反射可达性故障，并用精确的 `RuntimeHintsRegistrar` 修复。
- [x] 第 4 课：制造并修复 classpath 资源可达性问题。
- [x] 第 5 课：制造并修复 JDK 动态代理可达性问题。
- [x] 第 6 课：测试冷启动、预热、吞吐量、内存和镜像部署边界。

课程完整记录：

- [0002-buildpack-native-image-and-jvm-comparison.md](learning-records/0002-buildpack-native-image-and-jvm-comparison.md)
- [0003-local-native-compile-and-native-test.md](learning-records/0003-local-native-compile-and-native-test.md)
- [第二课离线 HTML：本地 nativeCompile 与 nativeTest](lessons/0002-local-native-compile-and-native-test.html)
- [第三课离线 HTML：用 RuntimeHints 修复反射可达性](lessons/0003-runtime-hints-reflection.html)
- [第三课学习记录](learning-records/0004-runtime-hints-reflection.md)
- [RuntimeHints 速查表](reference/runtime-hints-cheatsheet.html)
- [第四课离线 HTML：用 ResourceHints 打包动态资源](lessons/0004-resource-hints.html)
- [第四课学习记录](learning-records/0005-resource-hints.md)
- [第五课离线 HTML：用 ProxyHints 注册 JDK 动态代理](lessons/0005-jdk-proxy-hints.html)
- [第五课学习记录](learning-records/0006-jdk-proxy-hints.md)
- [第六课离线 HTML：正确比较 JVM 与 Native](lessons/0006-jvm-native-performance.html)
- [第六课学习记录](learning-records/0007-jvm-native-performance.md)
- [可复现基准说明](native-demo/benchmark/README.md)

## 六课主线完成

已经完成构建、AOT 概念、原生测试、reflection/resources/proxies
可达性修复以及 JVM/Native 实测选型。

后续可做一个真实第三方依赖兼容性扩展课，完成 `MISSION.md` 中最后一个尚未
单独验证的成功条件。当前项目快速回归：

```bash
cd native-demo
./gradlew test
./gradlew nativeTest
```
