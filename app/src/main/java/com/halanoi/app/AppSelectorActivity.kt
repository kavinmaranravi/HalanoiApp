package com.halanoi.app

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AppSelectorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Open our Local Database
        val sharedPrefs = getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
        val lockedApps = sharedPrefs.getStringSet("LOCKED_APPS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        // 2. Build the UI
        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(Color.parseColor("#121212"))
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 60, 40, 60)

        // --- TITLE ---
        val title = TextView(this)
        title.text = "Halanoi Vault\nSelect Apps to Lock"
        title.textSize = 24f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, Typeface.BOLD)
        title.setPadding(0, 0, 0, 40)
        layout.addView(title)

        // 3. Scan the phone
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val launchableApps = packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        for (appInfo in launchableApps) {
            val appName = pm.getApplicationLabel(appInfo).toString()
            val packageName = appInfo.packageName

            val checkBox = CheckBox(this)
            checkBox.text = appName
            checkBox.setTextColor(Color.LTGRAY)
            checkBox.textSize = 18f
            checkBox.setPadding(0, 20, 0, 20)

            // Original Logic: Locked apps are disabled to prevent unauthorized unlocking
            if (lockedApps.contains(packageName)) {
                checkBox.isChecked = true
                checkBox.isEnabled = false 
                checkBox.setTextColor(Color.GRAY) 
                checkBox.text = "$appName 🔒 (Locked)"
            }

            // When you check a box, it locks the app
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    lockedApps.add(packageName)
                    checkBox.isEnabled = false
                    checkBox.setTextColor(Color.GRAY)
                    checkBox.text = "$appName 🔒 (Locked)"
                    
                    // Save changes immediately
                    sharedPrefs.edit().putStringSet("LOCKED_APPS", lockedApps).apply()
                }
            }
            layout.addView(checkBox)
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
