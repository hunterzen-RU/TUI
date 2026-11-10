package ohi.andre.consolelauncher.managers

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Termux Terminal Session Architecture for Android 8.0+ (API 26 to 34+)
 * Supports dedicated login sessions, shell status queries, and direct shell command execution.
 */
class TermuxSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "TermuxSessionManager"

        @Volatile
        var isSessionActive: Boolean = false
            private set

        // Persistent StringBuilder output buffer to store session/command history
        private val outputBuffer = StringBuilder()

        fun setSessionState(active: Boolean) {
            isSessionActive = active
        }

        @Synchronized
        fun appendOutput(text: String) {
            if (outputBuffer.length > 100000) {
                outputBuffer.delete(0, outputBuffer.length - 50000)
            }
            outputBuffer.append(text)
        }

        @Synchronized
        fun getPersistentOutput(): String = outputBuffer.toString()

        @Synchronized
        fun clearPersistentOutput() {
            outputBuffer.setLength(0)
        }
    }

    data class SessionConfig(
        val homePath: String,
        val prefixPath: String,
        val tmpPath: String,
        val binPath: String,
        val envMap: Map<String, String>
    )

    data class ShellStatus(
        val isAvailable: Boolean,
        val activeSession: Boolean,
        val homeDir: String,
        val prefixDir: String,
        val pathEnv: String,
        val shellPath: String,
        val busyboxAvailable: Boolean
    )

    fun createSessionConfig(): SessionConfig {
        val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(null) ?: context.filesDir
        } else {
            context.filesDir
        }

        val homeDir = File(baseDir, "home").apply { if (!exists()) mkdirs() }
        val prefixDir = File(baseDir, "usr").apply { if (!exists()) mkdirs() }
        val binDir = File(prefixDir, "bin").apply { if (!exists()) mkdirs() }
        val tmpDir = File(context.cacheDir, "tmp").apply { if (!exists()) mkdirs() }

        val tuiBinDir = File(homeDir, "tui/bin").apply { if (!exists()) mkdirs() }
        val termuxBinDir = "/data/data/com.termux/files/usr/bin"
        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val fullPath = "${tuiBinDir.absolutePath}:${termuxBinDir}:${binDir.absolutePath}:$systemPath"

        val termuxPrefix = "/data/data/com.termux/files/usr"
        val env = mutableMapOf(
            "HOME" to homeDir.absolutePath,
            "PREFIX" to prefixDir.absolutePath,
            "TERMUX_PREFIX" to termuxPrefix,
            "TMPDIR" to tmpDir.absolutePath,
            "PATH" to fullPath,
            "LD_LIBRARY_PATH" to "$termuxPrefix/lib",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "ANDROID_DATA" to System.getenv("ANDROID_DATA").orEmpty(),
            "ANDROID_ROOT" to System.getenv("ANDROID_ROOT").orEmpty()
        )

        return SessionConfig(
            homePath = homeDir.absolutePath,
            prefixPath = prefixDir.absolutePath,
            tmpPath = tmpDir.absolutePath,
            binPath = binDir.absolutePath,
            envMap = env
        )
    }

    /**
     * Checks shell availability, environment paths, and active session status
     */
    fun checkStatus(): ShellStatus {
        val config = createSessionConfig()
        val shFile = File("/system/bin/sh")
        val busyboxBin = File(config.binPath, "busybox")

        return ShellStatus(
            isAvailable = shFile.exists() && shFile.canExecute(),
            activeSession = isSessionActive,
            homeDir = config.homePath,
            prefixDir = config.prefixPath,
            pathEnv = config.envMap["PATH"] ?: "",
            shellPath = if (shFile.exists()) shFile.absolutePath else "system default",
            busyboxAvailable = busyboxBin.exists()
        )
    }

    /**
     * Executes a raw shell command directly in Termux environment, bypassing launcher command interpreter
     */
    fun executeDirectShellCommand(command: String, timeoutSeconds: Long = 10): String {
        if (command.isBlank()) return "Empty shell command."

        val config = createSessionConfig()
        return try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", command)
            pb.directory(File(config.homePath))

            val env = pb.environment()
            env.putAll(config.envMap)

            val process = pb.start()
            val cmdOutputBuffer = StringBuilder()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    cmdOutputBuffer.append(line).append("\n")
                }
            }

            BufferedReader(InputStreamReader(process.errorStream)).use { errReader ->
                var errLine: String?
                while (errReader.readLine().also { errLine = it } != null) {
                    cmdOutputBuffer.append(errLine).append("\n")
                }
            }

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                cmdOutputBuffer.append("[Execution timed out after ${timeoutSeconds}s]")
            }

            val rawResult = cmdOutputBuffer.toString().trim()
            val finalResult = if (rawResult.isEmpty()) {
                "[Command executed with exit code ${process.exitValue()}]"
            } else {
                rawResult
            }

            appendOutput("$ $command\n$finalResult\n")
            finalResult
        } catch (e: Exception) {
            val errMessage = "Shell execution error: ${e.localizedMessage}"
            appendOutput("$ $command\n$errMessage\n")
            errMessage
        }
    }
}
