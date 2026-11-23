package com.ccm.elixir.native

import android.content.Context
import java.io.File

/**
 * 调用打包在 APK 里的 native 可执行文件。
 *
 * APK 里的 .so 不能直接 exec（在压缩的 apk 内），必须先复制到
 * 应用私有目录并 chmod +x。
 */
class NativeRunner(private val ctx: Context) {

    private val binDir: File by lazy {
        File(ctx.filesDir, "bin").apply { mkdirs() }
    }

    /** 编译产物名 -> 已就绪的可执行文件 */
    private val prepared = mutableMapOf<String, File>()

    fun prepare(name: String): File? {
        prepared[name]?.let { if (it.exists()) return it }

        val out = File(binDir, name)
        val abi = if (android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" })
            "arm64-v8a" else android.os.Build.SUPPORTED_ABIS[0]

        // Gradle 会把 add_executable 产物放进 lib/<abi>/
        val assetPath = "lib/$abi/lib$name.so"
        val ok = runCatching {
            ctx.assets.open(assetPath).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out.setExecutable(true, false)
            out.length() > 0
        }.getOrDefault(false)

        if (!ok) return null
        prepared[name] = out
        return out
    }

    /** 以 root 跑一次工具，返回 stdout 文本（stdout 为空返回空串） */
    fun execRoot(name: String, vararg args: String, timeoutSec: Int = 30): String {
        val bin = prepare(name) ?: return ""
        val cmd = buildString {
            append(bin.absolutePath)
            args.forEach { append(' ').append(it) }
        }
        val p = ProcessBuilder("su", "-c", cmd)
            .redirectErrorStream(false)
            .start()
        val out = StringBuilder()
        val err = StringBuilder()
        val tOut = Thread { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
        val tErr = Thread { p.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
        tOut.start(); tErr.start()

        val done = p.waitFor(timeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        if (!done) {
            p.destroyForcibly()
            tOut.join(500); tErr.join(500)
            return out.toString()
        }
        tOut.join(2000); tErr.join(2000)
        return out.toString()
    }

    /** 找目标游戏进程 pid，没有返回 -1 */
    fun gamePid(pkg: String = "com.tencent.tmgp.supercell.clashroyale"): Int {
        val txt = execRootOrSu("ps -A -o PID,NAME 2>/dev/null", 8)
        var best = -1
        txt.lineSequence().forEach { line ->
            val t = line.trim().split(Regex("\\s+"))
            if (t.size >= 2 && t[1] == pkg) {
                t[0].toIntOrNull()?.let { pid -> if (pid > best) best = pid }
            }
        }
        return best
    }

    private fun execRootOrSu(cmd: String, timeoutSec: Int): String {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val sb = StringBuilder()
        val t = Thread { p.inputStream.bufferedReader().forEachLine { sb.appendLine(it) } }
        t.start()
        val done = p.waitFor(timeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        if (!done) { p.destroyForcibly(); t.join(500); return sb.toString() }
        t.join(2000)
        return sb.toString()
    }
}