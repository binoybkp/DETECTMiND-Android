package com.research.detectmind.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class ScreenInteractionService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
