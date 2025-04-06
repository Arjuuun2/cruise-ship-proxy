package com.example.ship_proxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public String offshoreHost(@Value("${offshore.proxy.host}") String host) {
        return host;
    }

    @Bean
    public int offshorePort(@Value("${offshore.proxy.port}") int port) {
        return port;
    }
}
