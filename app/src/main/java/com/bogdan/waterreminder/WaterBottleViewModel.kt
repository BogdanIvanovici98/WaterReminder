package com.bogdan.waterreminder

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class WaterBottleViewModel : ViewModel() {
    val bubbles = mutableStateListOf<Bubble>()
}