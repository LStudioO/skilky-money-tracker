package com.vstorchevyi.skilky

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun `greet wraps the platform name in a Hello message`() {
        val greeting = Greeting().greet()
        assertTrue(greeting.startsWith("Hello, "), "got: $greeting")
        assertTrue(greeting.endsWith("!"), "got: $greeting")
    }
}
