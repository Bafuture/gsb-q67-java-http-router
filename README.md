# HTTP 路由与参数绑定组件

Pair-wise GSB 标注任务仓库（第 6 批 / 67）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 组件说明

实现位于 `src/main/java/com/example/gsb/router/`，核心入口是 `Router`：

```java
Router router = new Router()
        .route("GET", "/users/{id}", controller, "findById")
        .route("GET", "/files/**", controller, "download");

HttpResponse response = router.handle("GET", "/users/42?verbose=true");
```

处理方法通过注解绑定参数：`@PathVariable("id")` 绑定路径参数，
`@RequestParam("q")` 绑定查询参数（`required = false` 时缺省绑定 `null`）。

### 匹配规则

- **路径参数**：`{name}` 恰好匹配一个路径段，例如 `/users/{id}`。
- **通配后缀**：`**` 只能出现在最后一段，匹配零个或多个剩余段，捕获内容以 `/` 连接，
  绑定到名为 `**` 的路径变量，例如 `/files/**`。
- **优先级**：逐段从左到右比较，**静态段 > 参数段 > 通配段**。
  即 `/users/new` 优先于 `/users/{id}`，`/users/{id}` 优先于 `/users/**`。
  路由表（`router.table()`）即按此优先级排序导出。
- **大小写策略**：路径匹配**大小写敏感**（`/Users` 不匹配 `/users`）；
  HTTP 方法名大小写不敏感（`get` 与 `GET` 等价）。
- **尾斜杠策略**：忽略单个尾斜杠，`/users/` 与 `/users` 等价（注册与匹配两侧都做归一化）；
  根路径 `/` 是合法模式。
- **重复注册**：同一 HTTP 方法下，结构相同的模式（静态段逐段相同、段类型逐段相同，
  参数名无关）在注册时立即抛出 `DuplicateRouteException`。
  因此 `/users/{id}` 与 `/users/{name}` 也视为冲突。

### 参数绑定与类型转换

- 支持 `String`、全部基本类型及其包装类、`BigInteger`、`BigDecimal`、`UUID`、
  任意枚举（按常量名大小写敏感匹配）以及 `java.time` 的
  `LocalDate` / `LocalTime` / `LocalDateTime` / `OffsetDateTime` / `ZonedDateTime` / `Instant`
  （均为 ISO-8601 文本形式）。
- 布尔值严格解析，只接受 `true` / `false`（忽略大小写）。
- 转换失败返回 **400**，响应体包含参数名、原始值与目标类型；枚举失败时还会列出全部合法值。
- 缺少必填查询参数返回 **400**；无路由匹配返回 **404**；路径存在但方法不匹配返回 **405**；
  处理方法抛异常返回 **500**；返回 `void` 的方法成功时返回 **204**。

### 路由表导出

`router.table()` 把当前全部路由按匹配优先级导出为对齐的文本表格，例如：

```
| PRIORITY | METHOD | PATTERN     | HANDLER              |
|----------|--------|-------------|----------------------|
| 1        | GET    | /users/new  | UserController#newest |
| 2        | GET    | /users/{id} | UserController#byId   |
| 3        | GET    | /users/**   | UserController#all    |
```

### 已知限制

- 处理方法不能重载（按方法名查找，重载会在注册时报错）。
- 每个方法参数必须标注 `@PathVariable` 或 `@RequestParam`，不支持无注解参数、
  请求体绑定或自定义类型转换器扩展。
- 不支持单段通配符 `*`、正则段与可选段；`**` 只允许出现在末尾。
- 查询参数重复出现时只取第一个值；不支持数组/集合绑定。
- 枚举匹配大小写敏感；日期时间只接受 ISO-8601 格式，不支持自定义格式。
- 路由注册后不可移除，没有线程安全的动态重注册设计（注册与派发并发时需外部同步）。
- 路径段不做 URL 解码（查询参数会解码）；含编码斜杠 `%2F` 的路径不保证符合预期。

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们在写一个内部的小型 HTTP 服务骨架，需要自己实现请求到处理方法的映射。请从零实现一个 HTTP 路由与参数绑定组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持 `/users/{id}` 形式的路径参数与 `/files/**` 通配后缀，并明确匹配优先级（静态段优先于参数段，参数段优先于通配）；2) 把路径参数与查询参数绑定到方法参数，支持基本类型、枚举与日期时间，转换失败要返回明确错误；3) 同一路径重复注册同一 HTTP 方法时必须立即报错；4) 定义清楚大小写与尾斜杠策略，并有测试覆盖；5) 支持把当前全部路由按优先级导出为文本表格。测试覆盖匹配优先级、参数转换失败与重复注册，`mvn -q verify` 一条命令跑通，README 说明匹配规则与已知限制。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。
