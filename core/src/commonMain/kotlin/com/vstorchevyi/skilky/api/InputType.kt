package com.vstorchevyi.skilky.api

import kotlinx.serialization.Serializable

@Serializable
enum class InputType {
    TEXT,
    AUDIO,
    IMAGE,
}
