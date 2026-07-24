package com.example.nativedemo.greeting.proxy;

import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ImportRuntimeHints(ProxyGreetingRuntimeHints.class)
class ProxyGreetingController {

	private final ProxyGreetingService greetingService;

	ProxyGreetingController(ProxyGreetingService greetingService) {
		this.greetingService = greetingService;
	}

	@GetMapping("/proxy-hello")
	String proxyHello() {
		return greetingService.message("Native");
	}

}
