package com.example.nativedemo.greeting.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProxyGreetingTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createsConfiguredJdkDynamicProxy() throws Exception {
		mockMvc.perform(get("/proxy-hello"))
				.andExpect(status().isOk())
				.andExpect(content().string("HELLO, NATIVE!"));
	}

}
