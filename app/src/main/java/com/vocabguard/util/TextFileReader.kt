package com.vocabguard.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class NamedText(val displayName: String?, val text: String)

class FileTooLargeException(val limitBytes: Int) : IOException("File exceeds $limitBytes bytes")

/** Reads a user-picked text/CSV file. UTF-8 first, Windows-1250 fallback for Excel exports. */
object TextFileReader {
    const val MAX_BYTES = 1_000_000

    suspend fun read(context: Context, uri: Uri): NamedText = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val bytes = resolver.openInputStream(uri)?.use { readLimited(it) }
            ?: throw IOException("Couldn't open $uri")
        NamedText(name, decode(bytes))
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) throw FileTooLargeException(MAX_BYTES)
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1250"))
    }
}
