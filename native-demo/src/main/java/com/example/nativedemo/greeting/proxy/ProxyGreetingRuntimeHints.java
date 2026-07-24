package com.example.nativedemo.greeting.proxy;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public final class ProxyGreetingRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.reflection().registerType(ProxyGreeting.class);
		hints.proxies().registerJdkProxy(ProxyGreeting.class);
	}

}
