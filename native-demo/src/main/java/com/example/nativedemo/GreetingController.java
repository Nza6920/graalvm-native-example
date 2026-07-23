package com.example.nativedemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GreetingController {

	@GetMapping("/hello")
	String hello() {
		return "Hello from GraalVM!";
	}

}
