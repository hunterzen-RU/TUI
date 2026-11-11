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
        // Always use internal private app storage (context.filesDir) for executables and packages.
        // External storage (getExternalFilesDir) is mounted with 'noexec' on Android, which causes
        // "Permission denied" when attempting to execute proot or any binary.
        val baseDir = context.filesDir

        val oldExternal = context.getExternalFilesDir(null)
        if (oldExternal != null && oldExternal.exists()) {
            try {
                val oldHome = File(oldExternal, "home")
                val oldUsr = File(oldExternal, "usr")
                val targetHome = File(baseDir, "home")
                val targetUsr = File(baseDir, "usr")
                if (oldHome.exists() && !targetHome.exists()) {
                    oldHome.copyRecursively(targetHome, overwrite = true)
                }
                if (oldUsr.exists() && !targetUsr.exists()) {
                    oldUsr.copyRecursively(targetUsr, overwrite = true)
                }
            } catch (_: Exception) {}
        }

        val homeDir = File(baseDir, "home").apply { if (!exists()) mkdirs() }
        val prefixDir = File(baseDir, "usr").apply { if (!exists()) mkdirs() }
        val binDir = File(prefixDir, "bin").apply { if (!exists()) mkdirs() }
        val tmpDir = File(prefixDir, "tmp").apply { if (!exists()) mkdirs() }

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val tuiBinDir = File(homeDir, "tui/bin").apply { if (!exists()) mkdirs() }
        val termuxBinDir = "/data/data/com.termux/files/usr/bin"
        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val fullPath = "$nativeLibDir:${tuiBinDir.absolutePath}:${binDir.absolutePath}:${termuxBinDir}:$systemPath"

        val termuxPrefix = "/data/data/com.termux/files/usr"
        val prefixLib = File(prefixDir, "lib").apply { if (!exists()) mkdirs() }
        val prefixLibPath = prefixLib.absolutePath
        val libSubdirs = prefixLib.listFiles { f -> f.isDirectory }?.joinToString(":") { it.absolutePath }.orEmpty()
        val ldLibraryPath = listOfNotNull(
            nativeLibDir,
            prefixLibPath,
            if (libSubdirs.isNotEmpty()) libSubdirs else null,
            File(prefixLib, "pulseaudio").absolutePath,
            "$termuxPrefix/lib",
            System.getenv("LD_LIBRARY_PATH")
        ).filter { it.isNotBlank() }.distinct().joinToString(":")

        val env = mutableMapOf(
            "HOME" to homeDir.absolutePath,
            "PREFIX" to prefixDir.absolutePath,
            "TERMUX_PREFIX" to termuxPrefix,
            "TMPDIR" to tmpDir.absolutePath,
            "PROOT_TMP_DIR" to tmpDir.absolutePath,
            "PROOT_NO_SECCOMP" to "1",
            "PATH" to fullPath,
            "LD_LIBRARY_PATH" to ldLibraryPath,
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "ANDROID_DATA" to System.getenv("ANDROID_DATA").orEmpty(),
            "ANDROID_ROOT" to System.getenv("ANDROID_ROOT").orEmpty()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                android.system.Os.chmod(homeDir.absolutePath, 493) // 0755
                android.system.Os.chmod(prefixDir.absolutePath, 493)
                android.system.Os.chmod(binDir.absolutePath, 493)
                android.system.Os.chmod(tuiBinDir.absolutePath, 493)
                android.system.Os.chmod(tmpDir.absolutePath, 511) // 0777
            } catch (_: Exception) {}
        }

        // Auto-detect native binaries disguised as .so libraries in jniLibs / nativeLibraryDir
        val nativeLibFolder = File(nativeLibDir)
        if (nativeLibFolder.exists() && nativeLibFolder.isDirectory) {
            nativeLibFolder.listFiles()?.forEach { libFile ->
                val fileName = libFile.name
                // Check if file is executable or disguise pattern lib<name>_exec.so or lib<name>.so
                if (fileName.startsWith("lib") && fileName.endsWith(".so")) {
                    var baseCmd = fileName.removePrefix("lib").removeSuffix(".so")
                    if (baseCmd.endsWith("_exec")) {
                        baseCmd = baseCmd.removeSuffix("_exec")
                    }
                    if (baseCmd.isNotBlank()) {
                        val shimFile = File(tuiBinDir, baseCmd)
                        if (!shimFile.exists() || shimFile.length() < 50) {
                            val shimContent = StringBuilder()
                                .append("#!/system/bin/sh\n")
                                .append("export PREFIX=\"").append(prefixDir.absolutePath).append("\"\n")
                                .append("export HOME=\"").append(homeDir.absolutePath).append("\"\n")
                                .append("export PATH=\"$nativeLibDir:").append(tuiBinDir.absolutePath).append(":").append(binDir.absolutePath).append(":\$PATH\"\n")
                                .append("export LD_LIBRARY_PATH=\"$nativeLibDir:").append(prefixLibPath).append(":\$LD_LIBRARY_PATH\"\n")
                                .append("export TMPDIR=\"").append(tmpDir.absolutePath).append("\"\n")
                                .append("export TERM=\"xterm-256color\"\n")
                                .append("exec \"").append(libFile.absolutePath).append("\" \"$@\"\n")
                                .toString()
                            try {
                                shimFile.writeText(shimContent)
                                shimFile.setExecutable(true, false)
                                shimFile.setReadable(true, false)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    android.system.Os.chmod(shimFile.absolutePath, 493)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        val prefixBinFolder = File(binDir.absolutePath)
        if (prefixBinFolder.exists() && prefixBinFolder.isDirectory) {
            prefixBinFolder.listFiles()?.forEach { binFile ->
                if (binFile.isFile && binFile.canExecute()) {
                    AptPackageResolver.createLauncherShim(binFile, homeDir, prefixDir)
                }
            }
        }

        val prootBin = File(binDir, "proot")
        if (prootBin.exists()) {
            val prootShim = File(tuiBinDir, "proot")
            if (!prootShim.exists() || prootShim.length() < 100) {
                AptPackageResolver.createLauncherShim(prootBin, homeDir, prefixDir)
            }
        }

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
            val exports = "export PREFIX='${config.prefixPath}'; " +
                          "export HOME='${config.homePath}'; " +
                          "export PATH='${config.envMap["PATH"]}'; " +
                          "export LD_LIBRARY_PATH='${config.envMap["LD_LIBRARY_PATH"]}'; " +
                          "export TMPDIR='${config.tmpPath}'; " +
                          "export PROOT_TMP_DIR='${config.tmpPath}'; " +
                          "export PROOT_NO_SECCOMP=1; " +
                          "export TERM='xterm-256color'; "
            val fullCommand = exports + command
            val pb = ProcessBuilder("/system/bin/sh", "-c", fullCommand)
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

    /**
     * Directly executes native binaries contained in context.applicationInfo.nativeLibraryDir (e.g. libmpv_exec.so)
     * using ProcessBuilder with native execution speed.
     */
    fun executeNativeLibBinary(commandName: String, args: List<String> = emptyList(), timeoutSeconds: Long = 300): String {
        val config = createSessionConfig()
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        val cleanName = commandName.trim().removePrefix("lib").removeSuffix(".so").removeSuffix("_exec")
        val candidates = listOf(
            File(nativeLibDir, "lib${cleanName}_exec.so"),
            File(nativeLibDir, "lib${cleanName}.so"),
            File(nativeLibDir, cleanName)
        )

        val targetFile = candidates.firstOrNull { it.exists() }
            ?: return executeDirectShellCommand(listOf(commandName).plus(args).joinToString(" "), timeoutSeconds)

        return try {
            val cmdList = mutableListOf(targetFile.absolutePath)
            cmdList.addAll(args)

            val pb = ProcessBuilder(cmdList)
            pb.directory(File(config.homePath))

            val env = pb.environment()
            env.putAll(config.envMap)
            env["LD_LIBRARY_PATH"] = "$nativeLibDir:${config.envMap["LD_LIBRARY_PATH"] ?: ""}"

            val process = pb.start()
            val outputBuffer = StringBuilder()

            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            outputBuffer.append(line).append("\n")
                        }
                    }
                } catch (_: Exception) {}
            }

            val errReaderThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { errReader ->
                        var errLine: String?
                        while (errReader.readLine().also { errLine = it } != null) {
                            outputBuffer.append(errLine).append("\n")
                        }
                    }
                } catch (_: Exception) {}
            }

            readerThread.start()
            errReaderThread.start()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                outputBuffer.append("[Execution timed out after ${timeoutSeconds}s]")
            }

            try { readerThread.join(1000) } catch (_: Exception) {}
            try { errReaderThread.join(1000) } catch (_: Exception) {}

            val rawResult = outputBuffer.toString().trim()
            val finalResult = if (rawResult.isEmpty()) {
                "[Executed ${targetFile.name} with exit code ${process.exitValue()}]"
            } else {
                rawResult
            }

            appendOutput("$ $commandName ${args.joinToString(" ")}\n$finalResult\n")
            finalResult
        } catch (e: Exception) {
            val errMessage = "Native direct execution error: ${e.localizedMessage}"
            appendOutput("$ $commandName ${args.joinToString(" ")}\n$errMessage\n")
            errMessage
        }
    }
}
