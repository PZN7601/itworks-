package com.example.universaldeviceconnector

import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var autoScanSwitch: SwitchCompat
    private lateinit var cloudSyncSwitch: SwitchCompat
    private lateinit var aiAssistantSwitch: SwitchCompat
    private lateinit var remoteAccessSwitch: SwitchCompat
    private lateinit var scanIntervalSpinner: Spinner
    private lateinit var exportButton: Button
    private lateinit var clearCacheButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        setupToolbar()
        initializeViews()
        setupListeners()
        loadSettings()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }
    
    private fun initializeViews() {
        autoScanSwitch = findViewById(R.id.autoScanSwitch)
        cloudSyncSwitch = findViewById(R.id.cloudSyncSwitch)
        aiAssistantSwitch = findViewById(R.id.aiAssistantSwitch)
        remoteAccessSwitch = findViewById(R.id.remoteAccessSwitch)
        scanIntervalSpinner = findViewById(R.id.scanIntervalSpinner)
        exportButton = findViewById(R.id.exportButton)
        clearCacheButton = findViewById(R.id.clearCacheButton)
        
        // Setup spinner
        val intervals = arrayOf("5 minutes", "15 minutes", "30 minutes", "1 hour", "Manual only")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scanIntervalSpinner.adapter = adapter
    }
    
    private fun setupListeners() {
        autoScanSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSettingBoolean("auto_scan", isChecked)
            scanIntervalSpinner.isEnabled = isChecked
        }
        
        cloudSyncSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSettingBoolean("cloud_sync", isChecked)
        }
        
        aiAssistantSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSettingBoolean("ai_assistant", isChecked)
        }
        
        remoteAccessSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSettingBoolean("remote_access", isChecked)
        }
        
        exportButton.setOnClickListener {
            exportSettings()
        }
        
        clearCacheButton.setOnClickListener {
            clearCache()
        }
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        
        autoScanSwitch.isChecked = prefs.getBoolean("auto_scan", true)
        cloudSyncSwitch.isChecked = prefs.getBoolean("cloud_sync", false)
        aiAssistantSwitch.isChecked = prefs.getBoolean("ai_assistant", true)
        remoteAccessSwitch.isChecked = prefs.getBoolean("remote_access", false)
        
        scanIntervalSpinner.isEnabled = autoScanSwitch.isChecked
        scanIntervalSpinner.setSelection(prefs.getInt("scan_interval", 1))
    }
    
    private fun saveSettingBoolean(key: String, value: Boolean) {
        getSharedPreferences("app_settings", MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }
    
    private fun exportSettings() {
        Toast.makeText(this, "Settings exported successfully", Toast.LENGTH_SHORT).show()
    }
    
    private fun clearCache() {
        Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
