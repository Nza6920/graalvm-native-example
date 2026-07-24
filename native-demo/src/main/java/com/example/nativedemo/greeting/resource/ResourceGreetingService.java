package com.example.nativedemo.greeting.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class ResourceGreetingService {

	private final String resourcePath;

	ResourceGreetingService(@Value("${demo.greeting-resource}") String resourcePath) {
		this.resourcePath = resourcePath;
	}

	String message() {
		ClassLoader classLoader = ResourceGreetingService.class.getClassLoader();
		try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
			if (input == null) {
				throw new IllegalStateException(
						"Classpath resource not found: " + resourcePath);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
		}
		catch (IOException exception) {
			throw new IllegalStateException(
					"Unable to read classpath resource: " + resourcePath, exception);
		}
	}

}
