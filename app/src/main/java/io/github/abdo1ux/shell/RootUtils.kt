package io.github.abdo1ux.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Helper utility for Root detection, Root shell command execution,
 * and high-level system manipulation tasks.
 */
object RootUtils {

    enum class RebootMode(val command: String) {
        NORMAL("svc power reboot || reboot"),
        RECOVERY("svc power reboot recovery || reboot recovery"),
        BOOTLOADER("svc power reboot bootloader || reboot bootloader"),
        SOFT_REBOOT("setprop ctl.restart zygote")
    }

    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/vendor/bin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su"
    )

    private var cachedRootStatus: Boolean? = null

    /**
     * Checks if the SU binary exists on the device system path.
     */
    fun hasSuBinary(): Boolean {
        for (path in SU_PATHS) {
            if (File(path).exists()) return true
        }
        return false
    }

    /**
     * Comprehensive Root access verification.
     * Checks SU binary existence and executes a test 'id' query as root.
     */
    suspend fun isRootAvailable(forceCheck: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!forceCheck && cachedRootStatus != null) {
            return@withContext cachedRootStatus!!
        }

        if (!hasSuBinary()) {
            cachedRootStatus = false
            return@withContext false
        }

        val result = executeCommand("id", runAsRoot = true)
        val isRooted = result.isSuccess && result.stdout.contains("uid=0")
        cachedRootStatus = isRooted
        return@withContext isRooted
    }

    /**
     * Executes a shell command using root privileges (su) or standard shell (sh).
     */
    suspend fun executeCommand(
        command: String,
        runAsRoot: Boolean = false
    ): ShellResult = withContext(Dispatchers.IO) {
        val shell = if (runAsRoot) "su" else "sh"
        var process: Process? = null
        var os: DataOutputStream? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        try {
            process = Runtime.getRuntime().exec(shell)
            os = DataOutputStream(process.outputStream)

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
            }

            while (stderrReader.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            ShellResult(
                command = command,
                exitCode = exitCode,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                isRoot = runAsRoot
            )
        } catch (e: Exception) {
            ShellResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = e.localizedMessage ?: "Execution Exception",
                isRoot = runAsRoot
            )
        } finally {
            try {
                os?.close()
            } catch (_: Exception) {}
            process?.destroy()
        }
    }

    /**
     * Reboots the device in the specified mode (Requires Root).
     */
    suspend fun rebootDevice(mode: RebootMode): ShellResult {
        return executeCommand(mode.command, runAsRoot = true)
    }

    /**
     * Force stops an application by package name (Requires Root).
     */
    suspend fun forceStopApp(packageName: String): ShellResult {
        return executeCommand("am force-stop $packageName", runAsRoot = true)
    }

    /**
     * Clears package user data and cache (Requires Root).
     */
    suspend fun clearAppData(packageName: String): ShellResult {
        return executeCommand("pm clear $packageName", runAsRoot = true)
    }

    /**
     * Enables or Disables a package or component (Requires Root).
     */
    suspend fun setComponentEnabledState(componentOrPackage: String, enable: Boolean): ShellResult {
        val state = if (enable) "enable" else "disable"
        return executeCommand("pm $state $componentOrPackage", runAsRoot = true)
    }

    /**
     * Force stops background processes using standard shell/root.
     */
    suspend fun killBackgroundProcesses(): ShellResult {
        return executeCommand("am kill-all", runAsRoot = true)
    }

    /**
     * Sets a secure system setting (Requires Root).
     */
    suspend fun setSecureSetting(namespace: String, key: String, value: String): ShellResult {
        return executeCommand("settings put $namespace $key $value", runAsRoot = true)
    }
}
