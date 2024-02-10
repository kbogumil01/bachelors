package com.dispatcher.server.dispatcherServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@RestController
public class DispatcherServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DispatcherServerApplication.class, args);
	}
}
