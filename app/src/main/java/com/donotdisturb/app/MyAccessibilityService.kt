package com.donotdisturb.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var isBlocking = false
        private val ALLOWED = setOf("com.donotdisturb.app", "com.android.systemui")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isBlocking) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { pkg ->
                if (!ALLOWED.contains(pkg.toString())) {
                    val intent = Intent(this, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    override fun onInterrupt() {}
}
