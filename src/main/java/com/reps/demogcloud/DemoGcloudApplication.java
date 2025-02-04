package com.reps.demogcloud;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;





@SpringBootApplication
@EnableScheduling
public class DemoGcloudApplication {

	public static void main(String[] args) throws JsonProcessingException {
		SpringApplication.run(DemoGcloudApplication.class, args);


	}


}


