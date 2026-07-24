# JVM classpath 存在不代表资源进入 Native Image

用户能够正确判断：配置驱动的资源路径在 JVM 中可读，但没有 resource hint 时，自定义文本文件不会自动进入 Native Image。使用精确的 `registerPattern("greetings/resource-greeting.txt")` 后，原生测试由 3/4 通过变为 4/4 通过。

## Evidence

- 对根因检查题选择“资源文件没有注册进镜像”。
- JVM 端到端测试通过。
- 未注册 hints 时，原生测试稳定复现 `Classpath resource not found`。
- AOT metadata 包含目标资源 glob 后，4 个原生测试全部通过。

## Implications

后续遇到 Native 资源缺失时，可以先确认配置是否可读，再区分“路径错误”和“文件没有被构建期资源模式匹配”。资源提示应尽量使用精确路径，只有确实需要整个目录时才使用宽泛 glob。
