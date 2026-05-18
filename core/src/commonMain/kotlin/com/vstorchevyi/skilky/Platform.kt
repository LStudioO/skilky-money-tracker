package com.vstorchevyi.skilky

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
