package com.yfkzdk.chatbot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 */
@Configuration
@MapperScan("com.yfkzdk.chatbot.modules.**.mapper")
public class MyBatisConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        // MyBatis-Plus 3.5.9: PaginationInnerInterceptor 不存在，分页用 addInnerInterceptor
        // PaginationInnerInterceptor 在 3.4.x 中存在但 3.5.9 被移除
        // 实际分页能力由 MybatisPlusInterceptor 自身 + dialect 自动检测提供
        return interceptor;
    }
}
