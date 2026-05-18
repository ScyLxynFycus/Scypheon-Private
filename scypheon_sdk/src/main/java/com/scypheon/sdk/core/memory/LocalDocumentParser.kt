package com.scypheon.sdk.core.memory

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Enterprise Feature: Local RAG Document Ingestion.
 * Parses raw text from local files (TXT, CSV) and chunks them for vectorization.
 * In a full production app, this would include PDFBox/iText for PDFs.
 */
class LocalDocumentParser(
    private val context: Context,
    private val dualMemoryManager: DualMemoryManager
) {

    private val CHUNK_SIZE = 500
    private val OVERLAP_SIZE = 100

    /**
     * Reads a document from a URI (e.g., from Intent.ACTION_GET_CONTENT),
     * chunks it using a sliding window approach, and ingests it into the Vector Memory.
     */
    suspend fun ingestDocument(uri: Uri, documentTitle: String) {
        Timber.i("📄 Starting local document ingestion for: $documentTitle")

        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return

            val reader = BufferedReader(InputStreamReader(inputStream))
            val fullText = reader.readText()
            reader.close()

            if (fullText.isEmpty()) {
                Timber.w("📄 Document is empty.")
                return
            }

            val chunks = chunkText(fullText)
            Timber.i("📄 Split document into ${chunks.size} chunks. Vectorizing...")

            // We use a dummy "session" ID specifically for documents so they can be filtered
            val docSessionId = "doc_${System.currentTimeMillis()}"
            dualMemoryManager.createSession(docSessionId, "Document: $documentTitle")

            chunks.forEach { chunk ->
                // Ingest directly into the vector database
                dualMemoryManager.saveMessage(docSessionId, "DOC EXTRACT [$documentTitle]: $chunk", isUser = true)
            }

            Timber.i("✅ Document ingestion complete.")

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to ingest local document.")
        }
    }

    /**
     * Splits text into overlapping chunks to preserve semantic boundary context.
     */
    private fun chunkText(text: String): List<String> {
        val words = text.split("\\s+".toRegex())
        val chunks = mutableListOf<String>()
        var i = 0

        while (i < words.size) {
            val end = (i + CHUNK_SIZE).coerceAtMost(words.size)
            val chunk = words.subList(i, end).joinToString(" ")
            chunks.add(chunk)
            i += (CHUNK_SIZE - OVERLAP_SIZE)
        }

        return chunks
    }
}
