package com.example.nativedemo.greeting.reflective;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class ReflectiveGreetingService {

	private final String pluginClassName;

	ReflectiveGreetingService(@Value("${demo.greeting-plugin}") String pluginClassName) {
		this.pluginClassName = pluginClassName;
	}

	String message() {
		try {
			Class<?> pluginType = Class.forName(pluginClassName);
			Object plugin = pluginType.getDeclaredConstructor().newInstance();
			return (String) pluginType.getMethod("message").invoke(plugin);
		}
		catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(
					"Unable to load greeting plugin: " + pluginClassName, exception);
		}
	}

}
