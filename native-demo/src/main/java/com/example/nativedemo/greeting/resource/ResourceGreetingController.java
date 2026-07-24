package com.example.nativedemo.greeting.resource;

import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ImportRuntimeHints(ResourceGreetingRuntimeHints.class)
class ResourceGreetingController {

	private final ResourceGreetingService greetingService;

	ResourceGreetingController(ResourceGreetingService greetingService) {
		this.greetingService = greetingService;
	}

	@GetMapping("/resource-hello")
	String resourceHello() {
		return greetingService.message();
	}

}
