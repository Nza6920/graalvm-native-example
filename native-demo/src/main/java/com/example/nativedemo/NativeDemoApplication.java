package com.example.nativedemo;

import com.example.nativedemo.greeting.reflective.GreetingRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(GreetingRuntimeHints.class)
public class NativeDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(NativeDemoApplication.class, args);
	}

}
