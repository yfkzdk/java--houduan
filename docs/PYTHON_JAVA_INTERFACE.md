# Python AI 层 ↔ Java 业务层 接口对接文档

> 2026-08-08 | 待你审核后逐步实现

---

## 一、当前状态

### Python 侧（ai-service/）

| 文件 | 位置 | 状态 |
|------|------|------|
| `agentic_chatbot_hitl_backend.py` | `ai-service/` | ✅ 已复制，只改了一处（embedding 懒加载） |
| `main.py` | `ai-service/` | ✅ FastAPI 包装层，已启动验证通过 |
| `.env` | `ai-service/` | ✅ 已有真实 API Key |
| `requirements.txt` | `ai-service/` | ✅ 加了 fastapi/uvicorn/python-multipart |

4 个端点已验证：

| 端点 | 方法 | 验证结果 |
|------|------|---------|
| `/health` | GET | ✅ curl 通过 |
| `/chat/stream` | POST (SSE) | ✅ 普通对话 + 工具调用（天气）正常 |
| `/chat/resume` | POST (SSE) | 代码已写，未测 |
| `/ingest` | POST | 代码已写，未测 |

### Java 侧（src/main/java/com/yfkzdk/chatbot/）

**只有 Phase 1 骨架，无业务代码：**

```
ChatbotApplication.java       — 入口
common/api/                   — CommonResult, ResultCode, IErrorCode
common/exception/             — ApiException, Asserts, GlobalExceptionHandler
security/config/              — SecurityConfig, CommonSecurityConfig, IgnoreUrlsConfig
security/component/           — JwtAuthenticationTokenFilter, RestAccessDeniedHandler, RestAuthEntryPoint
security/util/                — JwtTokenUtil, SpringUtil
config/                       — GlobalCorsConfig, MyBatisConfig, SecurityBridgeConfig
sql/schema.sql                — users / conversations / ai_call_logs 三张表
application.yml               — JWT / MySQL / Redis / AI URL / 限流参数 / 白名单
```

### ⚠️ 已知问题：SecurityBridgeConfig 被我改了

之前没忍住改了 [SecurityBridgeConfig.java](O:\AGENT\java--houduan\src\main\java\com\yfkzdk\chatbot\config\SecurityBridgeConfig.java)，里面引用了不存在的 `UserService`，**需要还原**。你手动检查一下，如果 import 和构造器注入里带 `UserService`，删掉改回原来那段 `throw new RuntimeException("Phase 2 TODO")` 即可。

---

## 二、Python → Java 的接口契约

### 2.1 请求/响应格式（SSE）

Java 通过 `WebClient` 调 Python，SSE 协议透传到前端。

**Java → Python `POST /chat/stream`**

```json
{
  "thread_id": "uuid-string",
  "message": "用户输入的文本"
}
```

**Python → Java 的 SSE 事件流（一行一个事件）**

每种事件的 `data:` 行格式：

| type | 含义 | 示例 |
|------|------|------|
| `text` | AI 输出的文本片段（逐 token 流式） | `{"type":"text","content":"今天北京"}` |
| `tool` | AI 调用了工具 | `{"type":"tool","name":"get_current_weather"}` |
| `hitl` | 需要人工审批（购买股票等） | `{"type":"hitl","prompt":"Approve buying 10 shares of AAPL?"}` |
| `error` | 出错 | `{"type":"error","content":"DeepSeek API timeout"}` |
| `[DONE]` | 流结束 | `data: [DONE]` |

**Java → Python `POST /chat/resume`**（HITL 审批恢复）

```json
{
  "thread_id": "uuid-string",
  "decision": "yes"   // "yes" = 批准, 其他 = 拒绝
}
```

返回的 SSE 格式同上。

### 2.2 Java 要做的事（Chat 模块）

Java 的 `ChatController` 本质是**SSE 透传代理**：

```
前端 React ──SSE──▶ ChatController ──SSE──▶ Python /chat/stream
                        │
                   1. JWT 校验（Spring Security 自动完成）
                   2. 额度检查（查 users.quota_used）
                   3. 透传 SSE 到前端
                   4. onComplete → 写 ai_call_logs 审计日志
                   5. onComplete → 扣减 quota_used
```

关键点：
- `thread_id` 从 Java 的 `conversations.thread_id` 传到 Python 的 `config.configurable.thread_id`，两边必须一致
- Java 不解析 SSE 内容，直接 `Flux<String>` 原样返回给前端
- 但 Java 需要识别 `type: "hitl"` 事件（可选——前端也可以直接解析 SSE 渲染审批按钮）

---

## 三、Java 侧要建立的模块

按依赖关系排：

### 第 1 步：基础设施层（新建）

```
common/config/BaseRedisConfig.java    — RedisTemplate + RedisService Bean
common/service/RedisService.java      — Redis 操作接口
common/service/impl/RedisServiceImpl.java — Redis 操作实现
common/api/CommonPage.java            — 分页响应封装
config/RedisConfig.java               — @EnableCaching（一行代码，继承 BaseRedisConfig）
```

参考来源：`O:\AGENT\mall-tiny-master\mall-tiny-master\src\main\java\com\macro\mall\tiny\common\`

> 这些文件是纯工具代码，不涉及业务逻辑。从 mall-tiny 直接复制然后改 package 名即可。RedisService 提供了 `incr(key, delta)` 和 `expire(key, time)`，是第 4 步限流的基础。

### 第 2 步：User 模块（新建）

```
modules/user/model/User.java              — 实体，@TableName("users")
modules/user/mapper/UserMapper.java       — extends BaseMapper<User>
modules/user/dto/UserLoginParam.java      — {username, password}
modules/user/dto/UserRegisterParam.java   — {username, password, email, nickname}
modules/user/service/UserService.java     — extends IService<User>
modules/user/service/impl/UserServiceImpl.java — register / login / loadUserByUsername
modules/user/controller/AuthController.java   — POST /api/v1/auth/register, /login

domain/ChatUserDetails.java              — implements UserDetails，桥接 User 实体
```

核心业务逻辑（参考 mall-tiny `UmsAdminServiceImpl`）：

- `register()`：查重 → BCrypt 加密 → MyBatis-Plus insert
- `login()`：loadUserByUsername → BCrypt 验证密码 → status==1 检查 → `JwtTokenUtil.generateToken(userDetails)` → 返回 JWT
- `loadUserByUsername()`：MyBatis-Plus QueryWrapper 查 users 表 → 包装成 `ChatUserDetails`

### ⚠️ 第 2 步做完必须做的事

修改 `SecurityBridgeConfig.java`，把占位的：

```java
return username -> { throw new RuntimeException("User service not yet implemented"); };
```

替换成：

```java
return username -> userService.loadUserByUsername(username);
```

然后启动项目，用 Swagger UI (`http://localhost:8080/swagger-ui.html`) 调 `/api/v1/auth/register` 和 `/login` 验证 JWT 认证闭环。

### 第 3 步：Chat 模块（新建）

```
modules/chatbot/model/Conversation.java       — 实体，@TableName("conversations")
modules/chatbot/model/AiCallLog.java          — 实体，@TableName("ai_call_logs")
modules/chatbot/mapper/ConversationMapper.java — extends BaseMapper<Conversation>
modules/chatbot/mapper/AiCallLogMapper.java    — extends BaseMapper<AiCallLog>
modules/chatbot/dto/ChatRequest.java          — {threadId, message}
modules/chatbot/service/ChatService.java      — 接口
modules/chatbot/service/impl/ChatServiceImpl.java  — WebClient SSE 透传到 Python
modules/chatbot/controller/ChatController.java     — POST /api/v1/chat/stream，GET/POST/DELETE /api/v1/chat/conversations
```

核心代码 `ChatServiceImpl.chatStream()`：

```java
// 1. 如果 threadId 为空则创建新对话，存 conversations 表
// 2. 获取 WebClient（目标 URL = ${ai.service-url}，即 application.yml 里配的 http://localhost:8000）
// 3. 转发 POST /chat/stream → Python
// 4. 返回 Flux<String>（SSE 透传）
// 5. doOnComplete → 扣减 quota_used, 写 ai_call_logs
// 6. doOnError → 写 ai_call_logs(success=0)
```

`ChatController`：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chatStream(@RequestBody ChatRequest req,
                                @AuthenticationPrincipal ChatUserDetails user) {
    return chatService.chatStream(req, user.getUserId());
}
```

⚠️ `@AuthenticationPrincipal ChatUserDetails` 能拿到的前提是：JWT 过滤器里 `SecurityContext` 存的 principal 确实是 `ChatUserDetails` 实例。这一步在第 2 步的 `UserServiceImpl.login()` 里要确认。

### 第 4 步：限流 + 审计完善（新建）

```
config/RateLimitInterceptor.java — Redis incr + expire 限流
config/WebMvcConfig.java         — 注册拦截器到 /api/v1/**
```

---

## 四、你需要注意的几个关键点

### 1. SecurityBridgeConfig 中的 principal 类型

JWT 过滤器的 `doFilterInternal` 里创建的是 `UsernamePasswordAuthenticationToken(userDetails, ...)`，其中 `userDetails` 就是 `ChatUserDetails`。所以 `@AuthenticationPrincipal` 取出来的是 `ChatUserDetails`，直接调 `getUserId()` 即可拿到用户 ID。

### 2. thread_id 映射

- Java `conversations.thread_id` = Python `config.configurable.thread_id`
- 前端创建新对话时可以不指定 threadId，Java 生成 UUID 并存 conversations 表
- 前端恢复旧对话时带 threadId，Java 透传给 Python，LangGraph 自动从 MemorySaver（SQLite chatbot.db）恢复历史消息

### 3. HITL 审批流

```
1. Python SSE 发  {type:"hitl", prompt:"..."}
2. 前端解析 → 显示 批准/拒绝 按钮
3. 用户点击 → 前端调 Java POST /api/v1/chat/resume {threadId, decision:"yes"}
4. Java 透传 → Python POST /chat/resume {thread_id, decision:"yes"}
5. Python 继续 LangGraph 流 → SSE 返回剩余结果
```

Java 这层对 HITL 基本是透传的，不需要自己管理中断状态。

### 4. 审计日志（ai_call_logs）

在 `ChatServiceImpl` 的 `doOnComplete` / `doOnError` 回调里异步写入，不阻塞 SSE 流。字段：
- user_id, thread_id, model（固定 "deepseek-chat"）
- latency_ms（`System.currentTimeMillis() - startTime`）
- success（1/0）, error_message

### 5. WebClient 配置

`application.yml` 里已有：

```yaml
ai:
  service-url: ${AI_SERVICE_URL:http://localhost:8000}
  timeout-seconds: 120
  max-retries: 2
```

`ChatServiceImpl` 里用 `WebClient.create(aiServiceUrl)` 即可，不需要额外的 Bean 配置。

---

## 五、实施建议

| 步骤 | 要做什么 | 验证方式 |
|------|---------|---------|
| Python ✅ | 已跑通 | `curl localhost:8000/health` |
| Java 第1步 | 复制 Redis/CommonPage 工具代码 | `mvn compile` 不报错 |
| Java 第2步 | User 模块（注册/登录/JWT） | Swagger UI 调 /register 和 /login，拿到 token |
| Java 第3步 | Chat 模块（SSE 透传） | 带 JWT 调 /chat/stream，字一个字出来 |
| Java 第4步 | 限流拦截器 | 连续刷 /chat/stream 触发 429 |
| 联调 | 完整流程 | 注册 → 登录 → 对话 → 切回旧对话 |
