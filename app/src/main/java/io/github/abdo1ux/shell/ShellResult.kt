package io.github.abdo1ux.shell

/**
 * Encapsulates the output of a shell command execution.
 */
data class ShellResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isRoot: Boolean
) {
    val isSuccess: Boolean
        get() = exitCode == 0

    val output: String
        get() = when {
            stdout.isNotBlank() -> stdout.trim()
            stderr.isNotBlank() -> stderr.trim()
            else -> "Execution finished with exit code $exitCode"
        }
}
