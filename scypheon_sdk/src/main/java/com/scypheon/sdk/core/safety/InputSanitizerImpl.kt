package com.scypheon.sdk.core.safety

import android.util.Base64
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [InputSanitizer].
 * HELIOS Sentinel Architecture: Recursive Multi-Encoding Detection and Sanitization Pipeline.
 * Fully hardened against Deadlocks, ReDoS, GC thrashing, Zip Bombs, Homoglyphs, and Memory Leaks.
 */
@Singleton
class InputSanitizerImpl @Inject constructor(
    private val rateLimiter: SanitizerRateLimiter,
    private val auditLogger: SanitizerAuditLogger
) : InputSanitizer {

    companion object {
        private const val MAX_DECODE_DEPTH = 3
        private const val MAX_INPUT_SIZE_BYTES = 50_000     // 50KB input max
        private const val MAX_DECODED_SIZE_BYTES = 100_000  // 100KB output max
        private const val EXPANSION_RATIO_THRESHOLD = 10.0f
        private const val DECODE_TIMEOUT_MS = 100L

        // Pre-compiled Regexes to prevent ReDoS
        private val URL_HEX_PATTERN = Regex("%[0-9A-Fa-f]{2}")
        private val MORSE_STRUCTURE_PATTERN = Regex("^[.\\-]+(?:[ ]+[.\\-]+)*(?:[/]+[ ]*[.\\-]+(?:[ ]+[.\\-]+)*)*$")
        private val MORSE_ELLIPSIS_PATTERN = Regex("^[.]{2,10}$")
        private val MORSE_DASH_PATTERN = Regex("^[-]{2,10}$")
        
        private val INJECTION_PATTERNS = listOf(
            // English variants
            Regex("ignore.*(?:previous|all).*instructions", RegexOption.IGNORE_CASE),
            Regex("disregard.*(?:previous|all).*instructions", RegexOption.IGNORE_CASE),
            Regex("forget.*(?:previous|all).*rules", RegexOption.IGNORE_CASE),
            Regex("system\\s*prompt", RegexOption.IGNORE_CASE),
            Regex("reveal.*(?:system|hidden).*prompt", RegexOption.IGNORE_CASE),
            
            // Indonesian variants
            Regex("abaikan.*(?:instruksi|perintah).*sebelumnya", RegexOption.IGNORE_CASE),
            Regex("lupakan.*(?:aturan|instruksi).*sebelumnya", RegexOption.IGNORE_CASE),
            Regex("system\\s*prompt|prompt\\s*sistem", RegexOption.IGNORE_CASE),
            
            // Technical markers
            Regex("<\\|im_start\\|>|<\\|im_end\\|>", RegexOption.IGNORE_CASE),
            Regex("\\[INST\\]|\\[/INST\\]", RegexOption.IGNORE_CASE),
            Regex("<<SYS>>|<</SYS>>", RegexOption.IGNORE_CASE),
            Regex("###\\s*(?:Instruction|System|User|Assistant)", RegexOption.IGNORE_CASE),
            
            // Obfuscation detection
            Regex("[\\u200B-\\u200D\\uFEFF]{3,}"),  // Multiple zero-width chars
            Regex("[\\u202A-\\u202E]{2,}")  // Multiple bidi control chars
        )

        private val MORSE_MAP = mapOf(
            ".-" to "A", "-..." to "B", "-.-." to "C", "-.." to "D", "." to "E",
            "..-." to "F", "--." to "G", "...." to "H", ".." to "I", ".---" to "J",
            "-.-" to "K", ".-.." to "L", "--" to "M", "-." to "N", "---" to "O",
            ".--." to "P", "--.-" to "Q", ".-." to "R", "..." to "S", "-" to "T",
            "..-" to "U", "...-" to "V", ".--" to "W", "-..-" to "X", "-.--" to "Y",
            "--.." to "Z", "-----" to "0", ".----" to "1", "..---" to "2", "...--" to "3",
            "....-" to "4", "....." to "5", "-...." to "6", "--..." to "7", "---.." to "8",
            "----." to "9", "/" to " "
        )
    }

    // Thread-safe Entropy cache to prevent ConcurrentModificationException & Collision
    private val entropyCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Float>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean {
                return size > 100
            }
        }
    )

    override suspend fun sanitize(query: String): SanitizedInput {
        val startTime = System.currentTimeMillis()
        val inputHash = query.hashCode().toString()

        // 0. Rate limit check
        if (!rateLimiter.tryAcquire()) {
            Timber.w("🚨 [HELIOS] Rate limit exceeded")
            val result = SanitizedInput(text = "[SYSTEM_BLOCK: RATE_LIMIT_EXCEEDED]", wasModified = true)
            logAudit(inputHash, query.length, result.text.length, result.wasModified, System.currentTimeMillis() - startTime, true)
            return result
        }

        // 1. Unicode Normalization (Homoglyph defense)
        val normalized = Normalizer.normalize(query, Normalizer.Form.NFKC)

        if (normalized.length > MAX_INPUT_SIZE_BYTES) {
            Timber.w("🚨 [HELIOS] Input blocked: Exceeds 50KB limit")
            val result = SanitizedInput(text = "[SYSTEM_BLOCK: PAYLOAD_TOO_LARGE]", wasModified = true)
            logAudit(inputHash, query.length, result.text.length, result.wasModified, System.currentTimeMillis() - startTime, true)
            return result
        }

        val currentText = normalized.trim()
        val (finalText, wasDecoded) = recursiveSanitize(currentText, 0, emptySet())

        val cleaned = finalText.replace(Regex("\\s+"), " ")
        val isBlocked = cleaned.startsWith("[SYSTEM_BLOCK")

        val result = SanitizedInput(
            text = cleaned,
            wasModified = cleaned != query || wasDecoded
        )
        
        val timeMs = System.currentTimeMillis() - startTime
        Timber.i("🛡️ [HELIOS] Sanitization complete in ${timeMs}ms | Blocked: $isBlocked | Modified: ${result.wasModified}")
        
        logAudit(inputHash, query.length, result.text.length, result.wasModified, timeMs, isBlocked)
        
        return result
    }

    private fun logAudit(hash: String, inLen: Int, outLen: Int, modified: Boolean, timeMs: Long, blocked: Boolean) {
        auditLogger.logSanitization(hash, inLen, outLen, modified, timeMs, blocked)
    }

    private suspend fun recursiveSanitize(input: String, depth: Int, attemptedAtParent: Set<String>): Pair<String, Boolean> {
        if (depth >= MAX_DECODE_DEPTH) {
            Timber.w("🚨 [HELIOS] Max decode depth reached ($depth). Halting recursion to prevent DoS.")
            return Pair(input, false)
        }

        if (input.isBlank() || isLikelyNaturalLanguage(input)) {
            return Pair(input, false)
        }

        val probBase64 = calculateBase64Probability(input)
        val probHex = calculateHexProbability(input)
        val probUrl = calculateUrlProbability(input)
        val probBinary = calculateBinaryProbability(input)
        val probMorse = calculateMorseProbability(input)

        val decoders = listOf(
            "base64" to Pair(probBase64, ::decodeBase64),
            "hex" to Pair(probHex, ::decodeHex),
            "url" to Pair(probUrl, ::decodeUrl),
            "binary" to Pair(probBinary, ::decodeBinary),
            "morse" to Pair(probMorse, ::decodeMorse)
        ).sortedByDescending { it.second.first }

        val currentAttempted = mutableSetOf<String>().apply { addAll(attemptedAtParent) }

        for ((encoding, pair) in decoders) {
            val (prob, decoder) = pair
            if (prob < 0.8f) continue
            if (encoding in currentAttempted) continue

            currentAttempted.add(encoding)

            val decoded = decoder(input)
            if (decoded != null && decoded != input) {
                Timber.d("🛡️ [HELIOS] $encoding decoded at depth $depth")
                
                if (!validateDecodedContent(decoded)) {
                    Timber.w("🚨 [HELIOS] Decoded content failed validation, blocking")
                    return Pair("[SYSTEM_BLOCK: INJECTION_DETECTED]", true)
                }

                val recursiveResult = recursiveSanitize(decoded, depth + 1, currentAttempted)
                return Pair(recursiveResult.first, true)
            }
        }

        return Pair(input, false)
    }
    
    private suspend fun validateDecodedContent(decoded: String): Boolean {
        for (pattern in INJECTION_PATTERNS) {
            if (pattern.containsMatchIn(decoded)) {
                return false
            }
        }
        
        val injectionKeywords = listOf(
            "ignore", "disregard", "forget", "system", "prompt", "instructions",
            "abaikan", "lupakan", "instruksi", "perintah"
        )
        val words = decoded.split(" ")
        if (words.isNotEmpty()) {
            val keywordCount = injectionKeywords.count { keyword -> decoded.contains(keyword, ignoreCase = true) }
            val keywordDensity = keywordCount.toFloat() / words.size
            if (keywordDensity > 0.3f) {
                Timber.w("🚨 [HELIOS] High injection keyword density: $keywordDensity")
                return false
            }
        }
        return true
    }

    // --- Pre-Flight Checks & Entropy ---

    private fun isLikelyNaturalLanguage(input: String): Boolean {
        val words = input.split(" ").filter { it.isNotBlank() }
        if (words.size < 3) return false
        val hasSpaces = input.contains(" ")
        val hasMixedCase = input.any { it.isUpperCase() } && input.any { it.isLowerCase() }
        val avgWordLength = words.map { it.length }.average()
        return hasSpaces && hasMixedCase && avgWordLength in 3.0..8.0
    }

    private fun calculateShannonEntropy(input: String): Float {
        if (input.isEmpty()) return 0.0f
        
        // Fix P2: Use full string to avoid hash collisions
        entropyCache[input]?.let { return it }
        
        val charCounts = IntArray(65536)
        for (char in input) {
            charCounts[char.code]++
        }
        
        val len = input.length.toFloat()
        var entropy = 0.0
        
        for (count in charCounts) {
            if (count > 0) {
                val p = count / len
                entropy -= p * kotlin.math.ln(p)
            }
        }
        
        val result = (entropy / kotlin.math.ln(2.0)).toFloat()
        entropyCache[input] = result
        return result
    }

    // --- Heuristic Probability Detectors ---

    private fun calculateBase64Probability(input: String): Float {
        if (input.length < 16 || input.contains(" ")) return 0.0f
        if (input.length % 4 != 0) return 0.0f
        
        val validChars = input.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        val charRatio = validChars.toFloat() / input.length
        if (charRatio < 0.95f) return 0.0f
        
        val entropy = calculateShannonEntropy(input)
        if (entropy < 4.5f) return 0.3f
        
        val equalsCount = input.count { it == '=' }
        val paddingValid = when (equalsCount) {
            0 -> true
            1 -> input.endsWith("=")
            2 -> input.endsWith("==")
            else -> false
        }
        if (!paddingValid) return 0.2f
        
        val charCounts = IntArray(65536)
        var uniqueChars = 0
        for (char in input) {
            if (charCounts[char.code]++ == 0) uniqueChars++
        }
        
        val expectedUnique = minOf(64, input.length / 3)
        val distributionScore = (uniqueChars.toFloat() / expectedUnique).coerceAtMost(1.0f)
        
        return if (distributionScore > 0.6f && entropy > 4.5f) 0.9f else 0.4f
    }

    private fun calculateHexProbability(input: String): Float {
        val stripped = input.replace(" ", "")
        if (stripped.length < 4 || stripped.length % 2 != 0) return 0.0f
        val validChars = stripped.count { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        return validChars.toFloat() / stripped.length
    }

    private fun calculateUrlProbability(input: String): Float {
        if (!input.contains("%")) return 0.0f
        val hexMatchCount = URL_HEX_PATTERN.findAll(input).count()
        return if (hexMatchCount > 0) 0.85f else 0.0f
    }

    private fun calculateBinaryProbability(input: String): Float {
        val stripped = input.replace(" ", "")
        if (stripped.length < 8 || stripped.length % 8 != 0) return 0.0f
        val validChars = stripped.count { it == '0' || it == '1' }
        return validChars.toFloat() / stripped.length
    }

    private fun calculateMorseProbability(input: String): Float {
        if (input.length < 4) return 0.0f
        val morseChars = input.count { it == '.' || it == '-' || it == '/' || it == ' ' }
        val ratio = morseChars.toFloat() / input.length
        if (ratio < 0.9f) return 0.0f
        
        val hasValidStructure = MORSE_STRUCTURE_PATTERN.matches(input.trim())
        if (!hasValidStructure) return 0.1f
        
        if (MORSE_ELLIPSIS_PATTERN.matches(input)) return 0.0f
        if (MORSE_DASH_PATTERN.matches(input)) return 0.0f
        
        val letterCount = input.split(" ").count { it.isNotBlank() && !it.contains("/") }
        if (letterCount < 2) return 0.2f
        
        return 0.9f
    }

    // --- Strict Decoders with Timeouts & Size Limits ---

    private suspend fun decodeBase64(input: String): String? = withTimeoutOrNull(DECODE_TIMEOUT_MS) {
        try {
            val decodedBytes = Base64.decode(input, Base64.DEFAULT)
            if (decodedBytes.size > MAX_DECODED_SIZE_BYTES) return@withTimeoutOrNull null
            if (decodedBytes.size.toFloat() / input.length > EXPANSION_RATIO_THRESHOLD) return@withTimeoutOrNull null
            
            val str = String(decodedBytes, StandardCharsets.UTF_8)
            if (isPrintable(str)) str else null
        } catch (e: Exception) { null }
    }

    private suspend fun decodeHex(input: String): String? = withTimeoutOrNull(DECODE_TIMEOUT_MS) {
        try {
            val stripped = input.replace(" ", "")
            val decodedBytes = stripped.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (decodedBytes.size > MAX_DECODED_SIZE_BYTES) return@withTimeoutOrNull null
            
            val str = String(decodedBytes, StandardCharsets.UTF_8)
            if (isPrintable(str)) str else null
        } catch (e: Exception) { null }
    }

    private suspend fun decodeUrl(input: String): String? = withTimeoutOrNull(DECODE_TIMEOUT_MS) {
        try {
            URLDecoder.decode(input, StandardCharsets.UTF_8.name())
        } catch (e: Exception) { null }
    }

    private suspend fun decodeBinary(input: String): String? = withTimeoutOrNull(DECODE_TIMEOUT_MS) {
        try {
            val stripped = input.replace(" ", "")
            val decodedBytes = stripped.chunked(8).map { it.toInt(2).toByte() }.toByteArray()
            if (decodedBytes.size > MAX_DECODED_SIZE_BYTES) return@withTimeoutOrNull null
            
            val str = String(decodedBytes, StandardCharsets.UTF_8)
            if (isPrintable(str)) str else null
        } catch (e: Exception) { null }
    }

    private suspend fun decodeMorse(input: String): String? = withTimeoutOrNull(DECODE_TIMEOUT_MS) {
        try {
            val str = input.split(" ").mapNotNull { MORSE_MAP[it] }.joinToString("")
            if (str.isNotEmpty()) str else null
        } catch (e: Exception) { null }
    }

    private fun isPrintable(str: String): Boolean {
        if (str.isBlank()) return false
        return str.all { !it.isISOControl() || it.isWhitespace() }
    }
}

@Singleton
class SanitizerRateLimiter @Inject constructor() {
    // Thread-safe map to prevent memory leaks with custom eviction logic
    private val sessionWindows = Collections.synchronizedMap(
        object : LinkedHashMap<String, SessionWindow>(1000, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SessionWindow>?): Boolean {
                return size > 1000 || (System.currentTimeMillis() - (eldest?.value?.windowStart?.get() ?: 0L) > 120_000L)
            }
        }
    )
    
    private data class SessionWindow(
        val count: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0),
        val windowStart: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
    )
    
    companion object {
        private const val MAX_REQUESTS_PER_WINDOW = 100
        private const val WINDOW_SIZE_MS = 60_000L  // 1 minute
    }
    
    fun tryAcquire(sessionId: String = "global"): Boolean {
        val now = System.currentTimeMillis()
        
        val window = sessionWindows.getOrPut(sessionId) { SessionWindow() }
        
        val windowStart = window.windowStart.get()
        if (now - windowStart > WINDOW_SIZE_MS) {
            if (window.windowStart.compareAndSet(windowStart, now)) {
                window.count.set(1)
                return true
            }
        }
        
        return window.count.incrementAndGet() <= MAX_REQUESTS_PER_WINDOW
    }
}

@Singleton
class SanitizerAuditLogger @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val auditFile = java.io.File(context.filesDir, "helios_audit.log")
    // Non-blocking fire-and-forget I/O Coroutine Scope
    private val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun logSanitization(
        inputHash: String,
        inputLength: Int,
        outputLength: Int,
        wasModified: Boolean,
        processingTimeMs: Long,
        blocked: Boolean
    ) {
        val timestamp = System.currentTimeMillis()
        val logEntry = "[$timestamp] INPUT_HASH=$inputHash INPUT_LEN=$inputLength " +
                       "OUTPUT_LEN=$outputLength MODIFIED=$wasModified " +
                       "TIME_MS=$processingTimeMs BLOCKED=$blocked\n"
        
        writeScope.launch {
            try {
                auditFile.appendText(logEntry)
                if (auditFile.length() > 10_000_000) {
                    val rotatedFile = java.io.File(context.filesDir, "helios_audit_${timestamp}.log.old")
                    auditFile.renameTo(rotatedFile)
                    auditFile.createNewFile()
                    Timber.i("[HELIOS] Audit log rotated: ${rotatedFile.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to write to HELIOS audit log")
            }
        }
    }
}