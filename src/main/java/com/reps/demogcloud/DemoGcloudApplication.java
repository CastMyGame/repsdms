package com.reps.demogcloud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.models.punishment.StateFileRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.util.Collections;




@SpringBootApplication
@EnableScheduling
public class DemoGcloudApplication {

	public static void main(String[] args) throws JsonProcessingException {
		SpringApplication.run(DemoGcloudApplication.class, args);


	}


}


