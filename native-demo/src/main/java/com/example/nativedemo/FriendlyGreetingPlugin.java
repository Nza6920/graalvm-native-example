package com.example.nativedemo;

public class FriendlyGreetingPlugin implements GreetingPlugin {

	public FriendlyGreetingPlugin() {
	}

	@Override
	public String message() {
		return "Hello from a reflective plugin!";
	}

}
