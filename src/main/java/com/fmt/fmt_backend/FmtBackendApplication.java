package com.fmt.fmt_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync    // async email sending
@EnableCaching  // Caffeine cache for public endpoints
public class FmtBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FmtBackendApplication.class, args);
		System.out.println("✅ First Million Trade Backend Started Successfully!");
		System.out.println("📡 API Available at: http://localhost:8080");
		System.out.println("📊 Health Check: http://localhost:8080/actuator/health");
	}

}
