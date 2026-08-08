# agentic-chatbot Java Backend — Phase 1 提交

## 已完成

- [x] pom.xml — Spring Boot 3.4.2 + Java 21 + MyBatis-Plus + jjwt 0.12 + Knife4j
- [x] ChatbotApplication.java — 入口
- [x] application.yml + application-prod.yml — 配置 (MySQL, Redis, JWT, AI Service URL, Rate Limit, Swagger)
- [x] common/api — CommonResult<T> + ResultCode + IErrorCode
- [x] common/exception — ApiException + Asserts + GlobalExceptionHandler
- [x] security/config — SecurityConfig (Spring Boot 3.x SecurityFilterChain) + CommonSecurityConfig + IgnoreUrlsConfig
- [x] security/util — JwtTokenUtil (jjwt 0.12.x API) + SpringUtil
- [x] security/component — JwtAuthenticationTokenFilter + RestfulAccessDeniedHandler + RestAuthenticationEntryPoint
- [x] config — SecurityBridgeConfig + GlobalCorsConfig + MyBatisConfig
- [x] sql/schema.sql — users + conversations + ai_call_logs

## 待 Phase 2

- [ ] modules/user — User 实体 + Mapper + Service + AuthController (登录/注册)
- [ ] modules/chatbot — Conversation 实体 + ChatController (SSE 转发 Python)
- [ ] config/RateLimitInterceptor — 限流拦截器
- [ ] pom.xml 切换 MySQL 密码为 ${MYSQL_PASSWORD} 环境变量（当前 dev 默认 root）

## 启动方式

```bash
# 1. 先启动 MySQL + Redis
# 2. 执行 SQL
mysql -u root -p agentic_chatbot < sql/schema.sql
# 3. 启动
mvnw spring-boot:run
```

Swagger: http://localhost:8080/swagger-ui.html
测试账号: test / 123456
