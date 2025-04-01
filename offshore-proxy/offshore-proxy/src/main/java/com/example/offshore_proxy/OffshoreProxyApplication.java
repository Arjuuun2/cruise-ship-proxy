package com.example.offshore_proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class OffshoreProxyApplication {

	public static void main(String[] args) {

		ConfigurableApplicationContext context = SpringApplication.run(OffshoreProxyApplication.class, args);

		ProxyServer proxyServer = context.getBean(ProxyServer.class);
		proxyServer.start();
	}

}
