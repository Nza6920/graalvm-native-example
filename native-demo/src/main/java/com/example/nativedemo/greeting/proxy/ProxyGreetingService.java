package com.example.nativedemo.greeting.proxy;

import java.lang.reflect.Proxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class ProxyGreetingService {

	private final String interfaceName;

	ProxyGreetingService(
			@Value("${demo.greeting-proxy-interface}") String interfaceName) {
		this.interfaceName = interfaceName;
	}

	String message(String name) {
		try {
			Class<?> proxyInterface = Class.forName(interfaceName);
			Object proxy = Proxy.newProxyInstance(
					proxyInterface.getClassLoader(),
					new Class<?>[] { proxyInterface },
					(instance, method, arguments) ->
							"HELLO, " + arguments[0].toString().toUpperCase() + "!");
			return ((ProxyGreeting) proxy).format(name);
		}
		catch (ClassNotFoundException exception) {
			throw new IllegalStateException(
					"Proxy interface not found: " + interfaceName, exception);
		}
	}

}
