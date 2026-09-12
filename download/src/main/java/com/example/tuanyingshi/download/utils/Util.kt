package com.example.tuanyingshi.download.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun String.toLongOrDefault(defaultValue: Long): Long {
    return try {
        toLong()
    } catch (_: NumberFormatException) {
        defaultValue
    }
}

fun Long.formatSize(): String {
    require(this >= 0) { "Size must larger than 0." }

    val byte = this.toDouble()
    val kb = byte / 1024.0
    val mb = byte / 1024.0 / 1024.0
    val gb = byte / 1024.0 / 1024.0 / 1024.0
    val tb = byte / 1024.0 / 1024.0 / 1024.0 / 1024.0

    return when {
        tb >= 1 -> "${tb.decimal(2)} TB"
        gb >= 1 -> "${gb.decimal(2)} GB"
        mb >= 1 -> "${mb.decimal(2)} MB"
        kb >= 1 -> "${kb.decimal(2)} KB"
        else -> "${byte.decimal(2)} B"
    }
}

fun Double.decimal(digits: Int): Double {
    return this.toBigDecimal()
        .setScale(digits, RoundingMode.HALF_UP)
        .toDouble()
}

infix fun Long.ratio(bottom: Long): Double {
    if (bottom <= 0) {
        return 0.0
    }
    val result = (this * 100.0).toBigDecimal()
        .divide((bottom * 1.0).toBigDecimal(), 2,  RoundingMode.FLOOR)
    return result.toDouble()
}

suspend fun <T, R> (Collection<T>).parallel(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    max: Int = 2,
    action: suspend CoroutineScope.(T) -> R
): Iterable<R> = coroutineScope {
    val list = this@parallel
    if (list.isEmpty()) return@coroutineScope listOf<R>()

    val channel = Channel<T>()
    val output = Channel<R>()

    val counter = AtomicInteger(0)

    launch {
        list.forEach { channel.send(it) }
        channel.close()
    }

    repeat(max) {
        launch(dispatcher) {
            channel.consumeEach {
                output.send(action(it))
                val completed = counter.incrementAndGet()
                if (completed == list.size) {
                    output.close()
                }
            }
        }
    }

    val results = mutableListOf<R>()
    for (item in output) {
        results.add(item)
    }

    return@coroutineScope results
}

/**
 * 将十六进制字符串解码为字节数组。
 * m3u8 的 IV=0x... 是 32 位十六进制，对应 16 字节。
 */
fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(length / 2) { i ->
        val high = this[i * 2].digitToInt(16)
        val low = this[i * 2 + 1].digitToInt(16)
        ((high shl 4) or low).toByte()
    }
}

/**
 * AES-CBC 解密。
 * @param key 原始密钥字节（16/24/32 字节）
 * @param iv 原始 IV 字节（16 字节）
 */
fun ByteArray.decrypt(key: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val keySpec = SecretKeySpec(key, "AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
    return cipher.doFinal(this)
}
