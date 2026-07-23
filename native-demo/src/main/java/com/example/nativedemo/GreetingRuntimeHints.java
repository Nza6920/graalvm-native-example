package com.example.nativedemo;

import java.util.List;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public final class GreetingRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.reflection().registerType(FriendlyGreetingPlugin.class, type -> type
				.withConstructor(List.of(), ExecutableMode.INVOKE)
				.withMethod("message", List.of(), ExecutableMode.INVOKE));
	}

}
