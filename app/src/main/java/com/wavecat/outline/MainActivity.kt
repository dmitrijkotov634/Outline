package com.wavecat.outline

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.wavecat.outline.databinding.ActivityMainBinding
import com.wavecat.outline.services.OutlineService


class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(
            this
        )
    }

    private val pushNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.save.setOnClickListener {
            preferences.edit {
                putString("script", binding.script.text.toString())
                apply()
            }

            val service = OutlineService.instance

            if (service == null) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } else {
                service.loadScripts()
            }
        }

        binding.script.setText(preferences.getString("script", ""), TextView.BufferType.EDITABLE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pushNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}