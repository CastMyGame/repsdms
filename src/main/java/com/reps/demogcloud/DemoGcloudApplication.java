package com.reps.demogcloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class DemoGcloudApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoGcloudApplication.class, args);
	}

}
