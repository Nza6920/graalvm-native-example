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
- [ ] 第 5 课：制造并修复 JDK 动态代理可达性问题。
- [ ] 第 6 课：测试冷启动、预热、吞吐量、内存和镜像部署边界。

课程完整记录：

- [0002-buildpack-native-image-and-jvm-comparison.md](learning-records/0002-buildpack-native-image-and-jvm-comparison.md)
- [0003-local-native-compile-and-native-test.md](learning-records/0003-local-native-compile-and-native-test.md)
- [第二课离线 HTML：本地 nativeCompile 与 nativeTest](lessons/0002-local-native-compile-and-native-test.html)
- [第三课离线 HTML：用 RuntimeHints 修复反射可达性](lessons/0003-runtime-hints-reflection.html)
- [第三课学习记录](learning-records/0004-runtime-hints-reflection.md)
- [RuntimeHints 速查表](reference/runtime-hints-cheatsheet.html)
- [第四课离线 HTML：用 ResourceHints 打包动态资源](lessons/0004-resource-hints.html)
- [第四课学习记录](learning-records/0005-resource-hints.md)

## 下一课入口

第 5 课将添加一个 JVM 正常、Native 初始失败的 JDK 动态代理场景，然后用 proxy hints 修复。

开始前的快速基线：

```bash
cd native-demo
./gradlew test
```
