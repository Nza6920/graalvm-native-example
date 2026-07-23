package com.example.nativedemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReflectiveGreetingController {

	private final ReflectiveGreetingService greetingService;

	ReflectiveGreetingController(ReflectiveGreetingService greetingService) {
		this.greetingService = greetingService;
	}

	@GetMapping("/reflective-hello")
	String reflectiveHello() {
		return greetingService.message();
	}

}
