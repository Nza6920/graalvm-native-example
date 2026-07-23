# 教学备注

- 使用中文授课。
- 技术基线：Spring Boot 4.x、Gradle、JDK 25。
- 偏好快速进入可操作内容；先给结论，必要的兼容性风险简短说明。
- 每课聚焦一个小目标，并提供可立即执行的检查或练习。

## 当前进度

- [x] 第 1 课：使用 Buildpacks 构建 Native OCI 镜像，并与普通 JVM 镜像对比。
- [x] 第 2 课：使用 `nativeCompile` 构建本地原生可执行文件，并运行 `nativeTest`。
- [ ] 第 3 课：制造并修复反射、资源、动态代理与 `RuntimeHints` 问题。
- [ ] 第 4 课：测试冷启动、预热、吞吐量、内存和镜像部署边界。

课程完整记录：

- [0002-buildpack-native-image-and-jvm-comparison.md](learning-records/0002-buildpack-native-image-and-jvm-comparison.md)
- [0003-local-native-compile-and-native-test.md](learning-records/0003-local-native-compile-and-native-test.md)
- [第二课离线 HTML：本地 nativeCompile 与 nativeTest](lessons/0002-local-native-compile-and-native-test.html)

## 下一课入口

第 3 课将添加一个 JVM 正常、Native 初始失败的反射与资源场景，然后用 `RuntimeHintsRegistrar` 修复。

开始前的快速基线：

```bash
cd native-demo
./gradlew test
```
