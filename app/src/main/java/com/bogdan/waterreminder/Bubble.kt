package com.bogdan.waterreminder

data class Bubble(
    var x: Float,
    var y: Float,
    var radius: Float,
    var speed: Float,
    var alpha: Float,
    var active: Boolean = false,
    var toRemove: Boolean = false
)