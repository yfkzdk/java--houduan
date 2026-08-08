# mall-tiny → agentic-chatbot Java 后端 对接分析

> 2026-08-08 | 基于 mall-tiny v1.0.0 源码逐文件分析

---

## 一、mall-tiny 骨架拆解（你拿到的是什么）

mall-tiny 本质上是一个 **Spring Boot + RBAC 权限管理的脚手架**。作者把完整的 mall 电商项目的业务模块全部删了，只留了一个"后台用户权限管理"子系统（UMS），作为新项目的起点。

### 1.1 文件清单 & 职责

```
src/main/java/com/macro/mall/tiny/
│
├── MallTinyApplication.java          ← @SpringBootApplication 入口
│
├── common/                           ← 可复用通用层（不依赖任何业务）
│   ├── api/
│   │   ├── CommonResult.java         ← 统一响应 {code, message, data} ★
│   │   ├── CommonPage.java           ← 分页响应封装
│   │   ├── ResultCode.java           ← 5种状态码枚举
│   │   └── IErrorCode.java           ← 错误码接口
│   ├── config/
│   │   ├── BaseRedisConfig.java      ← Redis 序列化/缓存管理器基类 ★
│   │   └── BaseSwaggerConfig.java    ← Swagger 配置基类
│   ├── domain/
│   │   └── SwaggerProperties.java    ← Swagger 属性 DTO
│   ├── exception/
│   │   ├── ApiException.java         ← 自定义业务异常
│   │   ├── Asserts.java              ← 断言工具（抛异常专用）
│   │   └── GlobalExceptionHandler.java ← @ControllerAdvice 全局异常捕获 ★
│   └── service/
│       ├── RedisService.java         ← Redis 操作接口（String/Hash/Set/List） ★
│       └── impl/RedisServiceImpl.java
│
├── config/                           ← 项目级 Java Config（业务无关）
│   ├── GlobalCorsConfig.java         ← 全局跨域 ★
│   ├── MallSecurityConfig.java       ← 桥接：UserDetailsService + 动态权限数据源
│   ├── MyBatisConfig.java            ← MapperScan + 分页插件
│   ├── RedisConfig.java              ← @EnableCaching + 继承 BaseRedisConfig
│   └── SwaggerConfig.java            ← 继承 BaseSwaggerConfig
│
├── domain/
│   └── AdminUserDetails.java         ← Spring Security UserDetails 实现
│
├── modules/ums/                      ← ⚠️ 业务模块（你需要换成 chatbot）
│   ├── controller/UmsAdminController.java   ← /admin/* 全套 CRUD
│   ├── controller/UmsRoleController.java
│   ├── controller/UmsMenuController.java
│   ├── controller/UmsResourceController.java
│   ├── controller/UmsResourceCategoryController.java
│   ├── dto/                          ← 4个 DTO
│   ├── mapper/                       ← 9个 MyBatis-Plus Mapper
│   ├── model/                        ← 9个实体类
│   └── service/                      ← 9个 Service + Impl
│
├── security/                         ← Spring Security + JWT 全套 ★★★
│   ├── component/
│   │   ├── JwtAuthenticationTokenFilter.java  ← 从 Authorization 头解析 JWT
│   │   ├── DynamicAccessDecisionManager.java  ← URL级动态权限投票
│   │   ├── DynamicSecurityFilter.java         ← 动态权限拦截过滤器
│   │   ├── DynamicSecurityMetadataSource.java ← 权限元数据缓存
│   │   ├── DynamicSecurityService.java        ← 动态权限接口
│   │   ├── RestAuthenticationEntryPoint.java  ← 401 处理
│   │   └── RestfulAccessDeniedHandler.java    ← 403 处理
│   ├── config/
│   │   ├── SecurityConfig.java       ← SecurityFilterChain Bean（新版写法）★★★
│   │   ├── CommonSecurityConfig.java ← 所有 Security Bean 的工厂 ★★
│   │   └── IgnoreUrlsConfig.java     ← 白名单 URL 配置读取 ★
│   ├── util/
│   │   ├── JwtTokenUtil.java         ← JWT 生成/解析/刷新 ★★
│   │   └── SpringUtil.java           ← Spring 上下文工具
│   └── annotation/CacheException.java
│   └── aspect/RedisCacheAspect.java  ← Redis 故障容错 AOP
│
└── generator/
    └── MyBatisPlusGenerator.java     ← 代码生成器
```

带 ★ 的是**可以直接搬过来用、不需要改一行代码的**。

---

## 二、按你的需求逐项对齐

### 2.1 JWT 认证：直接复用

| 你需要什么 | mall-tiny 已有 | 评价 |
|-----------|---------------|------|
| 用户名密码登录 | `UmsAdminServiceImpl.login()` → BCrypt 验证 → 调用 `jwtTokenUtil.generateToken()` | ✅ 直接用 |
| 注册 | `UmsAdminController.register()` → BCrypt 加密存库 | ✅ 直接用 |
| JWT 生成 | `JwtTokenUtil.generateToken(UserDetails)` — HS512 签名，7天过期 | ✅ 直接用，改 yml 里的 secret |
| JWT 刷新 | `JwtTokenUtil.refreshHeadToken()` — 30分钟不重复刷新 | ✅ 直接用 |
| 请求拦截 | `JwtAuthenticationTokenFilter` — 从 `Authorization: Bearer xxx` 提取，解析，放入 SecurityContext | ✅ 直接用 |
| Security 配置 | `SecurityConfig.filterChain()` — 新版 SecurityFilterChain，无状态 Session，白名单 URL | ✅ 直接用 |

**你现在什么都不用写**，把 `security/` 整个包、`config/GlobalCorsConfig.java`、`config/MallSecurityConfig.java` 搬到新项目，ylm 配置好，登录/注册就通了。

### 2.2 限流：需要在现有 RedisService 上加 30 行

mall-tiny **没有限流**。但你有 `RedisServiceImpl`，已经封装了 `incr()`、`expire()`、`hIncr()`。

实现方案（基于现有 RedisService）：

```java
// LimitInterceptor.java — 新文件，30行
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    @Autowired private RedisService redisService;

    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String key = "rate:user:" + getUserId();  // 从 SecurityContext 取
        Long count = redisService.incr(key, 1);    // 现有方法
        if (count == 1) redisService.expire(key, 60L); // 60秒窗口
        if (count > 20) throw new ApiException("请求过于频繁");
        return true;
    }
}
```

然后注册到 `WebMvcConfigurer.addInterceptors()`，完事。

### 2.3 转发 Python AI 服务：需要新建一个 Controller

mall-tiny 没有转发层，你要新增 `modules/chatbot/` 包：

```
modules/chatbot/
├── controller/ChatController.java      ← Spring WebClient 转发 Python
├── service/ChatService.java            ← 封装转发逻辑
├── model/Conversation.java             ← 对话实体
├── mapper/ConversationMapper.java      ← MyBatis-Plus Mapper
── dto/ChatRequest.java                 ← DTO
```

核心代码：

```java
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final WebClient aiClient = WebClient.create("http://python-ai:8000");

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> chatStream(@RequestBody ChatRequest req) {
        // 1. 额度检查（查 MySQL）
        // 2. 转发 Python，透传 SSE
        return aiClient.post()
            .uri("/chat/stream")
            .bodyValue(req)
            .retrieve()
            .bodyToFlux(String.class)
            .doOnComplete(() -> deductQuota(...));
    }
}
```

SSE 透传大概 20 行，Spring Boot 原生 `WebClient` 不需要 langchain4j。

### 2.4 对话持久化：用 MyBatis-Plus 3 张表

mall-tiny 给的 MyBatis-Plus 配置（分页插件 + MapperScan + 代码生成器）全部可复用。你需要新建：

```sql
-- 对话会话表
CREATE TABLE conversations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    thread_id VARCHAR(64) NOT NULL UNIQUE,  -- 对应 Python 侧 LangGraph thread_id
    title VARCHAR(128) DEFAULT '新对话',
    created_at DATETIME DEFAULT NOW()
);

-- AI 调用日志表
CREATE TABLE ai_call_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    model VARCHAR(32),
    tokens_used INT,
    latency_ms INT,
    success TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT NOW()
);

-- 用户额度表（或直接在 users 表加字段）
ALTER TABLE users ADD quota_total INT DEFAULT 100;
ALTER TABLE users ADD quota_used INT DEFAULT 0;
```

然后用 MyBatis-Plus 的 `BaseMapper<T>` + `ServiceImpl<M extends BaseMapper<T>, T>` 一键生成 CRUD，和 mall-tiny 的 `UmsAdminService` 一样的写法。

---

## 三、哪些要留、哪些要删、哪些要改

### ✅ 直接搬（不动代码）

| 源路径 | 放哪 | 用途 |
|--------|------|------|
| `common/**` 全部 | 原样 | 统一响应、异常、Redis 服务 |
| `security/**` 全部 | 原样 | JWT + Spring Security |
| `config/GlobalCorsConfig.java` | 原样 | 跨域 |
| `config/MallSecurityConfig.java` | 原样（改注入的 Service） | 桥接用户加载 |
| `config/MyBatisConfig.java` | 原样 | MyBatis-Plus 配置 |
| `config/RedisConfig.java` | 原样 | Redis 配置 |
| `domain/AdminUserDetails.java` | 原样 | UserDetails 实现 |

### ❌ 删掉（电商业务的，你用不上）

| 文件 | 原因 |
|------|------|
| `modules/ums/model/UmsMenu.java` | 菜单表，你不是后台管理 |
| `modules/ums/model/UmsResource.java` | 资源表 |
| `modules/ums/model/UmsRole.java` | 角色表，可以简化 |
| `modules/ums/model/UmsRoleMenuRelation.java` | 角色菜单关联 |
| `modules/ums/model/UmsRoleResourceRelation.java` | 角色资源关联 |
| `modules/ums/model/UmsResourceCategory.java` | 资源分类 |
| 对应所有 Mapper/Service/Controller | 跟着删 |
| `security/component/DynamicSecurityFilter.java` | 动态权限（你不需要 URL 级权限） |
| `security/component/DynamicAccessDecisionManager.java` | 同上 |
| `security/component/DynamicSecurityMetadataSource.java` | 同上 |
| `security/component/DynamicSecurityService.java` | 同上 |
| `generator/MyBatisPlusGenerator.java` | 用完可删 |

### 🔧 要改的

| 文件 | 改什么 |
|------|--------|
| `application.yml` | 改 JWT secret（绝不能硬编码）、数据库名、Redis 配置 |
| `MallSecurityConfig.java` | `userDetailsService()` 注入你自己的 UserService |
| `UmsAdminServiceImpl.java` | 精简：只保留 register/login/loadByUsername |
| `modules/ums/model/UmsAdmin.java` | 改名 `User.java`，加 quota 字段 |
| `pom.xml` | 升级 Spring Boot 2.7.5 → 3.4.x + Java 17 |

### ➕ 要新建的

| 新建 | 作用 |
|------|------|
| `modules/chatbot/controller/ChatController.java` | 对话 API 入口，转发 Python |
| `modules/chatbot/service/ChatService.java` | 对话逻辑 + WebClient 调用 |
| `modules/chatbot/model/Conversation.java` | 对话实体 |
| `modules/chatbot/mapper/ConversationMapper.java` | Mapper |
| `config/RateLimitInterceptor.java` | 限流拦截器（基于现有 RedisService） |
| `config/WebMvcConfig.java` | 注册限流拦截器 |

---

## 四、改完后的项目结构

```
com.yfkzdk.chatbot/
├── ChatbotApplication.java
├── common/                    ← 从 mall-tiny 搬
│   ├── api/                   ← CommonResult, CommonPage, ResultCode
│   ├── config/                ← BaseRedisConfig
│   ├── exception/             ← GlobalExceptionHandler, ApiException
│   └── service/               ← RedisService + RedisServiceImpl
├── config/                    ← 从 mall-tiny 搬 + 改
│   ├── GlobalCorsConfig.java
│   ├── SecurityBridgeConfig.java    ← 原 MallSecurityConfig，精简
│   ├── MyBatisConfig.java
│   ├── RedisConfig.java
│   ├── WebMvcConfig.java           ← 新增：注册限流拦截器
│   └── RateLimitInterceptor.java   ← 新增：限流逻辑
├── domain/
│   └── ChatUserDetails.java        ← 原 AdminUserDetails
├── modules/
│   ├── user/                       ← 原 ums/，精简到只剩用户表
│   │   ├── controller/AuthController.java   ← 登录/注册
│   │   ├── model/User.java
│   │   ├── mapper/UserMapper.java
│   │   └── service/UserService.java
│   └── chatbot/                    ← 全新：你的核心业务
│       ├── controller/ChatController.java
│       ├── service/ChatService.java
│       ├── model/Conversation.java
│       └── mapper/ConversationMapper.java
└── security/                       ← 从 mall-tiny 全搬，删动态权限
    ├── component/
    │   ├── JwtAuthenticationTokenFilter.java
    │   ├── RestAuthenticationEntryPoint.java
    │   └── RestfulAccessDeniedHandler.java
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── CommonSecurityConfig.java
    │   └── IgnoreUrlsConfig.java
    └── util/
        ├── JwtTokenUtil.java
        └── SpringUtil.java
```

---

## 五、和 Python AI 服务的对接点

```
POST /api/v1/chat/stream
    │
    ▼
[ChatController]  鉴权（JWT 自动拦截）
    │ 额度检查
    │
    ▼
[ChatService]  →  WebClient (SSE)
    │
    │  HTTP POST  http://python-ai:8000/chat/stream
    │  Body: { thread_id, message }
    │
    ▼
[Python FastAPI]  你的 agentic_chatbot_hitl_backend.py
    │  LangGraph.chatbot.stream()
    │  SSE: text chunks + tool status + HITL prompts
    │
    ▼
[Java 透传 SSE → 前端 React]
```

两个关键对接点：

1. **thread_id 映射**：Java 侧 `conversations.thread_id` = Python 侧 `config.configurable.thread_id`
2. **HITL 审批**：Python SSE 里出现 `{type: "hitl", prompt: "..."}` 时，前端显示审批按钮，点确认后前端调 `POST /api/v1/chat/resume {thread_id, decision: "yes"}`

---

## 六、结论

**mall-tiny 是最合适的起点**，比 langchain4j 对你的项目匹配度高得多：

| 能力 | mall-tiny | langchain4j |
|------|-----------|-------------|
| JWT 认证 | ✅ 开箱即用（搬过来 0 代码） | ❌ 没有 |
| Redis 操作 | ✅ 完整封装 | ❌ 没有 |
| MyBatis-Plus | ✅ 分页+代码生成 | ❌ 没有 |
| 统一响应格式 | ✅ CommonResult | ❌ 没有 |
| 全局异常处理 | ✅ 已有 | ❌ 没有 |
| 对话记忆 | ❌ 需自建表 | ✅ ChatMemoryProvider |
| SSE 流式 | ❌ 需写 WebClient | ✅ Flux 原生支持 |
| AI 转发 | ❌ 需自写 Controller | ❌ 需"欺骗" ChatModel |
| 核心抽象匹配 | ✅ 纯粹 REST 后端 | ❌ 假设 Java 直接调 LLM |

你要改的东西量不大：删掉 RBAC 的 menu/resource/role 复杂表关系，建 3 张自己的表，写一个 ChatController 做 SSE 透传，加个限流拦截器。一两天的事。
