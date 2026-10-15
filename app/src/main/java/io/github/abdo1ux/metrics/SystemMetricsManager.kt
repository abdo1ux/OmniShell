package io.github.abdo1ux.metrics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Data classes holding structured system metrics.
 */
data class RamInfo(
    val totalBytes: Long,
    val availBytes: Long,
    val usedBytes: Long,
    val usedPercent: Int
)

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedPercent: Int
)

data class BatteryInfo(
    val levelPercent: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val health: String,
    val technology: String
)

data class SystemDeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val board: String,
    val uptimeFormatted: String
)

/**
 * Manager class to retrieve real-time hardware and system metrics.
 */
class SystemMetricsManager(private val context: Context) {

    private var lastTotalCpu: Long = 0
    private var lastIdleCpu: Long = 0

    /**
     * Reads current RAM statistics using ActivityManager.
     */
    fun getRamInfo(): RamInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val total = memInfo.totalMem
        val avail = memInfo.availMem
        val used = total - avail
        val percent = if (total > 0) ((used.toDouble() / total.toDouble()) * 100).toInt() else 0

        return RamInfo(total, avail, used, percent)
    }

    /**
     * Reads Internal Storage usage metrics via StatFs.
     */
    fun getStorageInfo(): StorageInfo {
        val path = Environment.getDataDirectory().path
        val stat = StatFs(path)

        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val freeBytes = availBlocks * blockSize
        val usedBytes = totalBytes - freeBytes

        val percent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0

        return StorageInfo(totalBytes, freeBytes, usedBytes, percent)
    }

    /**
     * Gets current battery stats from ACTION_BATTERY_CHANGED intent.
     */
    fun getBatteryInfo(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryInfo(0, false, 0f, 0, "Unknown", "Unknown")

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 0

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val healthStr = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Normal"
        }

        return BatteryInfo(percent, isCharging, temp, voltage, healthStr, tech)
    }

    /**
     * Estimates overall CPU load percentage from /proc/stat snapshot deltas.
     */
    fun getCpuUsagePercent(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine() ?: return 0
            reader.close()

            val toks = load.split("\\s+".toRegex())
            if (toks.size < 9) return 0

            val idle = toks[4].toLong() + toks[5].toLong()
            val total = toks.subList(1, 8).sumOf { it.toLongOrNull() ?: 0L }

            val totalDiff = total - lastTotalCpu
            val idleDiff = idle - lastIdleCpu

            lastTotalCpu = total
            lastIdleCpu = idle

            if (totalDiff == 0L) return 0

            val cpu = ((totalDiff - idleDiff).toDouble() / totalDiff.toDouble()) * 100
            cpu.coerceIn(0.0, 100.0).toInt()
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Retrieves static system information.
     */
    fun getDeviceInfo(): SystemDeviceInfo {
        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = uptimeMillis / (1000 * 60 * 60)
        val minutes = (uptimeMillis / (1000 * 60)) % 60
        val uptimeStr = String.format(Locale.US, "%dh %dm", hours, minutes)

        return SystemDeviceInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            board = Build.BOARD,
            uptimeFormatted = uptimeStr
        )
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            val gb = bytes.toDouble() / (1024 * 1024 * 1024)
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                val mb = bytes.toDouble() / (1024 * 1024)
                String.format(Locale.US, "%.0f MB", mb)
            }
        }
    }
}
