# HTTP 路由与参数绑定组件

一个零第三方依赖的轻量级 HTTP 路由骨架：注册「HTTP 方法 + 路径模式 + 处理方法」，
运行时按优先级匹配请求、完成类型转换并反射调用处理器。仅依赖 JDK 17；测试使用
JUnit 5 与 AssertJ。

## 运行方式

```bash
mvn -q verify
# 或使用 wrapper（无需本机安装 Maven）
./mvnw -q verify
```

## 快速上手

```java
class UserController {
  public String getUser(@PathParam("id") long id,
                        @QueryParam(value = "detail", required = false) Boolean detail) {
    return "user " + id;
  }

  public String download(@WildcardParam String remainder) {
    return "file: " + remainder;
  }
}

Router router = new Router();
router.register(HttpMethod.GET, "/users/{id}", new UserController(), "getUser");
router.register(HttpMethod.GET, "/files/**", new UserController(), "download");

router.dispatch("GET /users/42?detail=true"); // -> "user 42"
router.dispatch("GET /files/docs/a.pdf");     // -> "file: docs/a.pdf"
```

核心 API（`com.example.router.Router`）：

| 方法 | 说明 |
|------|------|
| `register(HttpMethod, String, Object, String)` | 按名称注册处理器（不支持重载方法） |
| `register(HttpMethod, String, Object, Method)` | 直接传 `Method`，用于重载方法 |
| `dispatch(String requestLine)` | 形如 `GET /users/42?role=ADMIN` 的一行式入口 |
| `dispatch(HttpMethod, path, query)` | 方法、路径、查询串分开传入 |
| `route(HttpMethod, path)` | 只匹配不调用，返回 `Optional<RouteMatch>` |
| `routes()` | 全部路由，按优先级排序 |
| `renderRouteTable()` | 导出为固定列宽文本表格 |

## 路径语法

- 路径以 `/` 开头，按 `/` 切分为段，段分为三类：
  - **字面段（literal）**：静态文本，如 `users`；
  - **参数段（parameter）**：`{id}`，恰好匹配一个路径段，名字需符合 `[A-Za-z_][A-Za-z0-9_]*`；
  - **通配段（wildcard）**：`**`，只能作为最后一段，匹配剩余的 **零个或多个** 段
    （`/files/**` 既能匹配 `/files/a/b`，也能匹配前缀本身 `/files`，此时捕获值为空串）。
- 不支持单段 `*` 通配（会在注册时立即报错）；不允许 `//` 空段、空参数名 `{}`
  以及同一路径内重复的参数名。

## 匹配优先级

从高到低依次比较：

1. **无通配符的模式优先于** 带 `/**` 的模式（即固定深度匹配始终胜过后缀通配）；
2. 固定段数量越多越优先；
3. 同一深度从左到右逐段比较：**字面段 > 参数段 > 通配段**；
4. 仍相同则按原始路径文本、再按 HTTP 方法名称给出确定次序（仅供导出排序）。

因此总体效果即题目要求的 **静态段 > 参数段 > 通配**。优先级与注册顺序无关。

结构上完全相同的两个模式（例如 `/users/{id}` 与 `/users/{userId}`）对匹配器不可区分，
注册同一 HTTP 方法时会立即抛出 `RouteRegistrationException`。

## 大小写与尾斜杠策略

- **大小写敏感**：字面路径段区分大小写（`/Users` 不会匹配 `/users`）；枚举参数值
  按枚举常量名精确匹配（`role=user` 不能绑定 `USER`）；查询参数名同样区分大小写。
  HTTP 方法接受规范的大写或小写拼写（`get` 与 `GET` 均可），但不接受 `gEt` 这类
  混合大小写。唯一不区分大小写的是布尔转换（`true`/`false`）。
- **尾斜杠不做归一化**：除根路径 `/` 外，任何以 `/` 结尾的请求路径都会抛出
  `IllegalArgumentException`，不会静默去掉斜杠，也不会做 301/308 跳转。注册时
  尾斜杠会被归一化一次（注册 `/users/` 等同于 `/users`），因此不会产生重复条目。

## 参数绑定

处理器每个参数必须恰好携带 `@PathParam`、`@QueryParam`、`@WildcardParam` 之一，
否则注册立即失败。

- 支持类型：`String`；`byte/short/int/long/float/double`（含包装类型）与
  `boolean/Boolean`；任意枚举；`LocalDate`、`LocalTime`、`LocalDateTime`
  （默认 ISO-8601，可用注解的 `pattern = "dd/MM/yyyy"` 自定义）以及 `Instant`
  （固定 ISO-8601，自定义 pattern 会在注册时报错）。
- 不支持 `char/Character` 及其他类型（注册时报错）。
- 路径参数恒为必填；查询参数可用 `@QueryParam(value = "x", required = false)`
  声明可选，缺省时传入 `null`，因此可选参数必须是引用类型（基本类型无法为 `null`，
  注册时立即报错）。
- 路径与查询值按 UTF-8 做百分号解码，查询串中的 `+` 解码为空格；重复的查询键
  保留最后一次出现的值。
- 转换失败抛出 `ParamBindingException`，错误信息包含参数位置（path/query）、
  参数名、原始值与目标类型；枚举转换失败还会列出全部允许值。

## 错误类型

| 异常 | 时机 | 建议 HTTP 语义 |
|------|------|----------------|
| `RouteRegistrationException` | 注册期：重复路由、非法模式、处理器签名不支持 | 启动失败（500 类配置错误） |
| `NoRouteException` | 路径无匹配；`allowedMethods()` 非空表示方法不允许 | 404 / 405 + `Allow` 头 |
| `ParamBindingException` | 参数缺失或类型转换失败 | 400 |
| `RouteInvocationException` | 处理器自身抛异常，`getCause()` 为原始异常 | 按业务处理 |
| `IllegalArgumentException` | 请求行/路径非法（尾斜杠、空段等） | 400 |

同一路径对同一 HTTP 方法重复注册会在第二次 `register` 调用时**立即**抛错，
且路由表保持原样（第一次注册仍然有效）。

## 路由表导出

`renderRouteTable()` 输出固定列宽的纯文本表格，`PRIORITY` 即上节规则算出的顺序：

```
PRIORITY  METHOD  PATH         HANDLER
--------  ------  -----------  -------
1         GET     /files/meta  C#list
2         POST    /files/{id}  C#get
3         GET     /users/{id}  C#get
4         GET     /files/**    C#dl
```

## 测试

- `RouteMatchingPriorityTest`：静态/参数/通配优先级、注册顺序无关、左段决胜；
- `ParameterBindingTest`：基本类型、枚举、日期时间（ISO 与自定义 pattern）、
  UTF-8 解码，以及各类转换失败与注册期签名校验；
- `RouteRegistrationTest`：重复注册立即报错、同路径不同方法共存、结构歧义、非法模式；
- `CaseAndTrailingSlashTest`：大小写敏感、尾斜杠策略、405 allowedMethods；
- `RouteTableExportTest`、`WildcardRouteTest`：导出排序与通配捕获语义。

共 47 个测试用例，`mvn -q verify` 一条命令跑通。

## 已知限制

- 本组件只负责「路由 + 参数绑定」，不含真实 HTTP Server、序列化/响应封装、
  Content-Type 协商、过滤器/拦截器或异步支持；上层服务需自行把异常映射为状态码。
- 通配符只能出现在结尾；不支持参数段内的正则约束（如 `{id:\\d+}`），也不支持
  可选段、矩阵参数。
- 查询参数只保留单值（重复键取最后一个），不支持 `List<T>` 集合绑定；
  不绑定请求头、Cookie 与请求体。
- 处理器通过反射调用，要求是 `target` 对象上可访问的 public 方法；方法内部抛出的
  受检/非受检异常统一包装为 `RouteInvocationException`。
- 匹配基于已拆分的原始路径段（仅对捕获值做百分号解码），因此字面段里出现 `%xx`
  时按字面文本比较。
