package com.example.ship_proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ShipPoxyApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(ShipPoxyApplication.class, args);

		ProxyHandler proxyHandler = context.getBean(ProxyHandler.class);
		proxyHandler.start();
	}

}
