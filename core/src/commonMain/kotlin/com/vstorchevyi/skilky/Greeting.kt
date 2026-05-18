package com.vstorchevyi.skilky

class Greeting {
    private val platform = getPlatform()

    fun greet(): String = "Hello, ${platform.name}!"
}
