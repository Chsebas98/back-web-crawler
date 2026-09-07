package com.web.crawler.web_crawler.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * spring-boot-starter-security is on the classpath, which by default locks
 * every endpoint behind a generated login. This exercise's REST API has no
 * authentication requirement, so security is intentionally opened up here
 * rather than removing the dependency - keeping it documents that the choice
 * was deliberate, not an oversight, and leaves a clear place to add real
 * authentication/authorization later.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
