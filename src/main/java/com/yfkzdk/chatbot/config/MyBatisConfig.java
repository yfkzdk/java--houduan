package com.yfkzdk.chatbot.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * <p>
 * MyBatis-Plus 3.5.9 没有 PaginationInnerInterceptor 类。
 * 分页由 MybatisPlusInterceptor 自动检测方言完成。
 */
@Configuration
@MapperScan("com.yfkzdk.chatbot.modules.**.mapper")
public class MyBatisConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return new MybatisPlusInterceptor();
    }
}
