package com.evops.config;

import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiroConfig {
    @Bean
    public Realm localRealm() {
        SimpleAccountRealm realm = new SimpleAccountRealm();
        realm.addAccount("bootstrap", "bootstrap", "system");
        return realm;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chain = new DefaultShiroFilterChainDefinition();
        chain.addPathDefinition("/api/health", "anon");
        chain.addPathDefinition("/api/auth/login", "anon");
        chain.addPathDefinition("/error", "anon");
        // 水产监管月报口径管理：缺权闸门在认证之前生效（bean 名 caliberAccess 由 report 模块提供）。
        // 服务内另有一道与 URL 无关的闸门，换调用地址到达口径管理逻辑同样拒绝。
        chain.addPathDefinition("/api/aqua/report/calibers", "caliberAccess,authc");
        chain.addPathDefinition("/api/aqua/report/calibers/**", "caliberAccess,authc");
        chain.addPathDefinition("/**", "authc");
        return chain;
    }
}
