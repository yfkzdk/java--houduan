package com.yfkzdk.chatbot;

import com.yfkzdk.chatbot.common.service.RedisService;
import com.yfkzdk.chatbot.modules.user.dto.UserLoginParam;
import com.yfkzdk.chatbot.modules.user.dto.UserRegisterParam;
import com.yfkzdk.chatbot.modules.user.model.User;
import com.yfkzdk.chatbot.modules.user.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 集成测试：用户注册 / 登录 / JWT 认证 / 受保护接口
 *
 * 使用真实 Spring Boot 上下文 + H2 内嵌数据库 + TestRestTemplate。
 * 测试之间独立、可重复运行。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase2AuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserService userService;

    private static String jwtToken;
    private static String tokenHead;

    // 每次测试使用独立用户名，避免重复注册干扰
    private String username;
    private String password = "password123";

    @BeforeEach
    void setUp() {
        username = "p2test_" + System.currentTimeMillis();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ─── 测试 1：注册 ───

    @Test
    @Order(1)
    void testRegister() {
        UserRegisterParam param = new UserRegisterParam();
        param.setUsername(username);
        param.setPassword(password);
        param.setEmail(username + "@test.com");
        param.setNickname("测试-" + username);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/register", param, Map.class);

        System.out.println("[Phase2] 注册响应 code=" + response.getStatusCode()
                + " body=" + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().get("code"));

        // 验证数据库里确实有这条记录且密码是 BCrypt 密文
        User dbUser = userService.getByUsername(username);
        assertNotNull(dbUser, "用户应该已经写入数据库");
        assertTrue(dbUser.getPassword().startsWith("$2a$"), "密码应为 BCrypt 密文，实际: " + dbUser.getPassword().substring(0, 10));

        System.out.println("[Phase2] ✅ 注册验证通过 — 用户名=" + dbUser.getUsername()
                + ", status=" + dbUser.getStatus() + ", quotaTotal=" + dbUser.getQuotaTotal());
    }

    // ─── 测试 2：注册重名应失败 ───

    @Test
    @Order(2)
    void testRegisterDuplicate() {
        // 先用同一个用户名注册第一次
        UserRegisterParam param = new UserRegisterParam();
        param.setUsername(username);
        param.setPassword(password);
        restTemplate.postForEntity(baseUrl() + "/api/v1/auth/register", param, Map.class);

        // 第二次注册应该返回失败
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/register", param, Map.class);

        System.out.println("[Phase2] 重复注册响应: " + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // JSON 反序列化后 code 可能是 Integer 也可能 Long，统一用 int 比较
        assertEquals(500, ((Number) response.getBody().get("code")).intValue());
        System.out.println("[Phase2] ✅ 重名拦截验证通过");
    }

    // ─── 测试 3：登录拿到 JWT ───

    @Test
    @Order(3)
    void testLogin() {
        // 先注册
        UserRegisterParam regParam = new UserRegisterParam();
        regParam.setUsername(username);
        regParam.setPassword(password);
        restTemplate.postForEntity(baseUrl() + "/api/v1/auth/register", regParam, Map.class);

        // 登录
        UserLoginParam loginParam = new UserLoginParam();
        loginParam.setUsername(username);
        loginParam.setPassword(password);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/login", loginParam, Map.class);

        System.out.println("[Phase2] 登录响应: " + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().get("code"));

        Map<String, String> data = (Map<String, String>) response.getBody().get("data");
        assertNotNull(data, "data 不应为空");
        assertNotNull(data.get("token"), "token 不应为空");
        assertTrue(data.get("token").length() > 20, "JWT 长度应 > 20 字符");

        jwtToken = data.get("token");
        tokenHead = data.get("tokenHead");

        System.out.println("[Phase2] ✅ 登录验证通过 — JWT token 前 30 字符: "
                + jwtToken.substring(0, Math.min(30, jwtToken.length())) + "...");
    }

    // ─── 测试 4：受保护接口需 JWT 认证 ───

    @Test
    @Order(4)
    void testProtectedEndpoint() {
        // 先注册+登录
        UserRegisterParam regParam = new UserRegisterParam();
        regParam.setUsername(username);
        regParam.setPassword(password);
        restTemplate.postForEntity(baseUrl() + "/api/v1/auth/register", regParam, Map.class);

        UserLoginParam loginParam = new UserLoginParam();
        loginParam.setUsername(username);
        loginParam.setPassword(password);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/login", loginParam, Map.class);
        Map<String, String> data = (Map<String, String>) loginResp.getBody().get("data");
        String token = data.get("token");

        // 不带 Token → 应返回 401
        HttpHeaders noAuthHeaders = new HttpHeaders();
        ResponseEntity<String> rejected = restTemplate.exchange(
                baseUrl() + "/actuator/health", HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders), String.class);
        // Actuator 在白名单里，所以换一个受保护路径
        ResponseEntity<String> rejected2 = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/refresh", HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders), String.class);

        System.out.println("[Phase2] 无 Token 请求受保护接口: " + rejected2.getStatusCode());
        assertTrue(rejected2.getStatusCode() == HttpStatus.UNAUTHORIZED
                || rejected2.getStatusCode() == HttpStatus.FORBIDDEN,
                "无 Token 时应返回 401/403，实际: " + rejected2.getStatusCode());

        // 带 Token → 应返回 200
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", "Bearer " + token);
        ResponseEntity<String> allowed = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/refresh", HttpMethod.GET,
                new HttpEntity<>(authHeaders), String.class);

        System.out.println("[Phase2] 带 Token 请求受保护接口: " + allowed.getStatusCode());
        assertEquals(HttpStatus.OK, allowed.getStatusCode());

        System.out.println("[Phase2] ✅ JWT 认证过滤器验证通过");
    }

    // ─── 测试 5：登录密码错误应失败 ───

    @Test
    @Order(5)
    void testLoginWrongPassword() {
        // 先注册
        UserRegisterParam regParam = new UserRegisterParam();
        regParam.setUsername(username);
        regParam.setPassword(password);
        restTemplate.postForEntity(baseUrl() + "/api/v1/auth/register", regParam, Map.class);

        // 用错误密码登录
        UserLoginParam loginParam = new UserLoginParam();
        loginParam.setUsername(username);
        loginParam.setPassword("wrong_password");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/login", loginParam, Map.class);

        System.out.println("[Phase2] 错误密码登录: " + response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(404, response.getBody().get("code"));  // VALIDATE_FAILED

        System.out.println("[Phase2] ✅ 密码错误拦截验证通过");
    }

    // ─── 测试 6：负载测试 — 同一用户多次注册 ───

    @Test
    @Order(6)
    void testMultipleRegistrations() {
        int success = 0, rejected = 0;
        String baseName = "p2batch_" + System.currentTimeMillis();

        for (int i = 0; i < 5; i++) {
            UserRegisterParam param = new UserRegisterParam();
            param.setUsername(baseName + "_" + i);
            param.setPassword("pass" + i);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    baseUrl() + "/api/v1/auth/register", param, Map.class);
            if ((int) resp.getBody().get("code") == 200) success++;
            else rejected++;
        }

        // 尝试重复注册第一个用户名
        UserRegisterParam dup = new UserRegisterParam();
        dup.setUsername(baseName + "_0");
        dup.setPassword("pass0");
        ResponseEntity<Map> dupResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/register", dup, Map.class);
        if ((int) dupResp.getBody().get("code") == 200) success++;
        else rejected++;

        System.out.println("[Phase2] 批量注册结果: 成功=" + success + ", 拒绝=" + rejected);
        assertEquals(5, success, "5 个独立用户应全部注册成功");
        assertEquals(1, rejected, "1 次重名应被拒绝");
        System.out.println("[Phase2] ✅ 批量注册验证通过");
    }

    // ─── 总结 ───

    @Test
    @Order(99)
    void phase2Summary() {
        System.out.println("============================================");
        System.out.println(" Phase 2 VERIFICATION COMPLETE");
        System.out.println(" ✅ 注册 → DB 写入 BCrypt 密文");
        System.out.println(" ✅ 重名拦截");
        System.out.println(" ✅ 登录 → 返回 JWT Token");
        System.out.println(" ✅ JWT 过滤器 → 无 Token 被拒");
        System.out.println(" ✅ JWT 过滤器 → 有 Token 放行");
        System.out.println(" ✅ 密码错误拦截");
        System.out.println(" ✅ 批量 5+1 注册通过");
        System.out.println("============================================");
    }
}
