package io.github.abdo1ux

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.abdo1ux.adapter.AppAdapter
import io.github.abdo1ux.adapter.AppModel
import io.github.abdo1ux.databinding.ActivityMainBinding
import io.github.abdo1ux.metrics.SystemMetricsManager
import io.github.abdo1ux.shell.RootUtils
import io.github.abdo1ux.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Activity for OmniShell.
 * Universal responsive dashboard providing real-time system metrics,
 * non-root display/audio controls, package management, and root shell actions.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var metricsManager: SystemMetricsManager
    private lateinit var audioManager: AudioManager
    private lateinit var appAdapter: AppAdapter

    private var isRootGranted = false
    private var isMetricsActive = true
    private var allAppsList = listOf<AppModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        metricsManager = SystemMetricsManager(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        setupHeaderAndDeviceInfo()
        setupAppManagerRecyclerView()
        setupBrightnessControls()
        setupAudioVolumeControls()
        setupPowerAndRootActions()
        setupShellTerminalConsole()

        // Initial Root Verification
        checkRootAccess()

        // Start Periodic Metrics Updates
        startMetricsMonitoring()

        // Load Package List
        loadInstalledPackages()
    }

    override fun onResume() {
        super.onResume()
        checkSettingsWritePermission()
        updateAudioSliders()
    }

    override fun onDestroy() {
        super.onDestroy()
        isMetricsActive = false
    }

    /**
     * Header information and Root verification setup.
     */
    private fun setupHeaderAndDeviceInfo() {
        val deviceInfo = metricsManager.getDeviceInfo()
        binding.tvDeviceInfo.text = getString(
            R.string.device_info_format,
            deviceInfo.manufacturer,
            deviceInfo.model,
            deviceInfo.androidVersion,
            deviceInfo.sdkInt
        )

        binding.btnVerifyRoot.setOnClickListener {
            checkRootAccess(forceCheck = true)
        }
    }

    /**
     * Checks if Root (SU) access is granted.
     * Updates Root Badge UI and root state flag.
     */
    private fun checkRootAccess(forceCheck: Boolean = false) {
        binding.tvRootBadge.text = getString(R.string.root_status_checking)
        binding.tvRootBadge.setTextColor(getColor(R.color.status_nonroot_amber))

        lifecycleScope.launch {
            isRootGranted = RootUtils.isRootAvailable(forceCheck)
            if (isRootGranted) {
                binding.tvRootBadge.text = getString(R.string.root_status_active)
                binding.tvRootBadge.setTextColor(getColor(R.color.status_root_green))
                binding.cbRunAsRoot.isChecked = true
                Toast.makeText(this@MainActivity, "Root Access Confirmed!", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvRootBadge.text = getString(R.string.root_status_inactive)
                binding.tvRootBadge.setTextColor(getColor(R.color.status_nonroot_amber))
                binding.cbRunAsRoot.isChecked = false
                if (forceCheck) {
                    showRootFallbackDialog("Root Access Not Detected")
                }
            }
        }
    }

    /**
     * Standard Graceful Root Check Helper.
     * Executes the root action block if Root is present; otherwise presents clean fallback dialog.
     */
    private fun executeRootFeature(featureName: String, actionBlock: suspend () -> ShellResult) {
        if (!isRootGranted) {
            showRootFallbackDialog(featureName)
            return
        }

        lifecycleScope.launch {
            appendTerminalLog("Executing $featureName (Root)...")
            val result = actionBlock()
            appendTerminalLog("Result [Exit Code ${result.exitCode}]:\n${result.output}")
            Toast.makeText(this@MainActivity, "$featureName: Exit Code ${result.exitCode}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Displays a clean fallback warning dialog when Root feature is attempted without root access.
     */
    private fun showRootFallbackDialog(featureName: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.root_required_dialog_title)
            .setMessage("The feature '$featureName' requires Root (SU) privileges.\n\nYour device is currently running in standard Non-Root mode. Advanced system modifications are restricted.")
            .setPositiveButton("Verify Root Again") { dialog, _ ->
                dialog.dismiss()
                checkRootAccess(forceCheck = true)
            }
            .setNegativeButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Real-time metrics monitoring loop for CPU, RAM, Storage, and Battery.
     */
    private fun startMetricsMonitoring() {
        lifecycleScope.launch(Dispatchers.Default) {
            while (isActive && isMetricsActive) {
                val cpuPercent = metricsManager.getCpuUsagePercent()
                val ramInfo = metricsManager.getRamInfo()
                val storageInfo = metricsManager.getStorageInfo()
                val batteryInfo = metricsManager.getBatteryInfo()

                withContext(Dispatchers.Main) {
                    // Update CPU UI
                    binding.tvCpuVal.text = "$cpuPercent%"
                    binding.pbCpu.progress = cpuPercent

                    // Update RAM UI
                    binding.tvRamVal.text = "${ramInfo.usedPercent}% (${SystemMetricsManager.formatBytes(ramInfo.usedBytes)})"
                    binding.pbRam.progress = ramInfo.usedPercent

                    // Update Storage UI
                    binding.tvStorageVal.text = "${storageInfo.usedPercent}% (${SystemMetricsManager.formatBytes(storageInfo.freeBytes)} Free)"
                    binding.pbStorage.progress = storageInfo.usedPercent

                    // Update Battery UI
                    val chargingState = if (batteryInfo.isCharging) "Charging" else "Discharging"
                    binding.tvBatteryVal.text = "${batteryInfo.levelPercent}% ($chargingState)"
                    binding.pbBattery.progress = batteryInfo.levelPercent
                }

                delay(1500)
            }
        }
    }

    /**
     * Screen Brightness and Auto-Brightness non-root controls.
     */
    private fun setupBrightnessControls() {
        checkSettingsWritePermission()

        binding.btnGrantSettingsPerm.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        // Initialize current brightness slider
        try {
            val currentBrightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            binding.sbBrightness.value = currentBrightness.toFloat().coerceIn(0f, 255f)
        } catch (_: Exception) {}

        binding.sbBrightness.addOnChangeListener { _, value, fromUser ->
            if (fromUser && Settings.System.canWrite(this)) {
                try {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        value.toInt()
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to adjust brightness: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Auto Brightness Mode Switch
        try {
            val mode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            binding.switchAutoBrightness.isChecked = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (_: Exception) {}

        binding.switchAutoBrightness.setOnCheckedChangeListener { _, isChecked ->
            if (Settings.System.canWrite(this)) {
                val newMode = if (isChecked) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, newMode)
            } else {
                checkSettingsWritePermission()
            }
        }

        // Screen Rotate Button
        binding.btnRotateScreen.setOnClickListener {
            val currentOrientation = resources.configuration.orientation
            requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            Toast.makeText(this, R.string.msg_screen_rotated, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSettingsWritePermission() {
        val canWrite = Settings.System.canWrite(this)
        binding.btnGrantSettingsPerm.visibility = if (canWrite) View.GONE else View.VISIBLE
    }

    /**
     * Audio Volume sliders configuration using AudioManager.
     */
    private fun setupAudioVolumeControls() {
        updateAudioSliders()

        binding.sbMediaVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setStreamVolumePercent(AudioManager.STREAM_MUSIC, value)
        }

        binding.sbRingVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setStreamVolumePercent(AudioManager.STREAM_RING, value)
        }

        binding.sbAlarmVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setStreamVolumePercent(AudioManager.STREAM_ALARM, value)
        }
    }

    private fun updateAudioSliders() {
        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (maxMusic > 0) binding.sbMediaVolume.value = ((curMusic.toFloat() / maxMusic.toFloat()) * 100)

        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val curRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        if (maxRing > 0) binding.sbRingVolume.value = ((curRing.toFloat() / maxRing.toFloat()) * 100)

        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val curAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        if (maxAlarm > 0) binding.sbAlarmVolume.value = ((curAlarm.toFloat() / maxAlarm.toFloat()) * 100)
    }

    private fun setStreamVolumePercent(streamType: Int, percent: Float) {
        val maxVol = audioManager.getStreamMaxVolume(streamType)
        val newVol = ((percent / 100f) * maxVol).toInt()
        audioManager.setStreamVolume(streamType, newVol, 0)
    }

    /**
     * Power and Root Quick Action Panel.
     */
    private fun setupPowerAndRootActions() {
        binding.btnReboot.setOnClickListener {
            executeRootFeature("Reboot System") {
                RootUtils.rebootDevice(RootUtils.RebootMode.NORMAL)
            }
        }

        binding.btnRebootRecovery.setOnClickListener {
            executeRootFeature("Reboot Recovery") {
                RootUtils.rebootDevice(RootUtils.RebootMode.RECOVERY)
            }
        }

        binding.btnForceStopBg.setOnClickListener {
            executeRootFeature("Force Stop Background Apps") {
                RootUtils.killBackgroundProcesses()
            }
        }

        binding.btnClearCaches.setOnClickListener {
            executeRootFeature("Clear Caches") {
                RootUtils.executeCommand("pm trim-caches 100G", runAsRoot = true)
            }
        }
    }

    /**
     * App Manager RecyclerView and search filter setup.
     */
    private fun setupAppManagerRecyclerView() {
        appAdapter = AppAdapter(
            onItemClick = { app -> openAppInfo(app.packageName) },
            onForceStopClick = { app ->
                executeRootFeature("Force Stop ${app.appName}") {
                    RootUtils.forceStopApp(app.packageName)
                }
            }
        )

        binding.rvAppList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
        }

        binding.etSearchApp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAppList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.switchSystemApps.setOnCheckedChangeListener { _, _ ->
            filterAppList()
        }
    }

    /**
     * Asynchronously loads installed packages.
     */
    private fun loadInstalledPackages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

            val list = packages.mapNotNull { pkgInfo ->
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                AppModel(
                    appName = label,
                    packageName = pkgInfo.packageName,
                    icon = icon,
                    isSystemApp = isSys
                )
            }.sortedBy { it.appName.lowercase() }

            allAppsList = list

            withContext(Dispatchers.Main) {
                filterAppList()
            }
        }
    }

    private fun filterAppList() {
        val query = binding.etSearchApp.text.toString().trim().lowercase()
        val showSystem = binding.switchSystemApps.isChecked

        val filtered = allAppsList.filter { app ->
            val matchesQuery = query.isEmpty() ||
                    app.appName.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query)
            val matchesType = showSystem || !app.isSystemApp
            matchesQuery && matchesType
        }

        appAdapter.submitList(filtered)
    }

    private fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open App Info: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Custom Shell Command Console execution.
     */
    private fun setupShellTerminalConsole() {
        binding.btnRunCommand.setOnClickListener {
            val cmd = binding.etShellCommand.text.toString().trim()
            if (cmd.isEmpty()) {
                Toast.makeText(this, "Please enter a shell command", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val runAsRoot = binding.cbRunAsRoot.isChecked
            if (runAsRoot && !isRootGranted) {
                showRootFallbackDialog("Root Shell Command Execution")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                appendTerminalLog("$ ${if (runAsRoot) "[su]" else "[sh]"} $cmd")
                val result = RootUtils.executeCommand(cmd, runAsRoot = runAsRoot)
                appendTerminalLog("Output (Exit ${result.exitCode}):\n${result.output}\n---")
            }
        }
    }

    private fun appendTerminalLog(logText: String) {
        val current = binding.tvShellOutput.text.toString()
        val updated = "$current\n\n$logText"
        binding.tvShellOutput.text = updated
    }
}
