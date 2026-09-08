package com.calltech

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache

object ContactLookupHelper {
    private val cache = LruCache<String, String>(256)

    fun resolveDisplayName(context: Context, address: String?): String {
        val raw = address?.trim().orEmpty()

        if (raw.isBlank() || raw == "Unknown") {
            return raw
        }

        if (!raw.any { it.isDigit() }) {
            return raw
        }

        cache.get(raw)?.let { return it }

        val normalizedNumbers = buildLookupNumbers(raw)

        normalizedNumbers.forEach { number ->
            cache.get(number)?.let { cached ->
                cache.put(raw, cached)
                return cached
            }
        }

        val resolver = context.applicationContext.contentResolver

        normalizedNumbers.forEach { number ->
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number),
                )

                resolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex =
                            cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)

                        if (nameIndex >= 0) {
                            val name = cursor.getString(nameIndex)?.trim().orEmpty()

                            if (name.isNotBlank()) {
                                cache.put(raw, name)
                                cache.put(number, name)
                                return name
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        cache.put(raw, raw)
        return raw
    }

    private fun buildLookupNumbers(raw: String): List<String> {
        val digits = raw.filter { it.isDigit() || it == '+' }

        if (digits.isBlank()) {
            return listOf(raw)
        }

        val variants = linkedSetOf(raw, digits)

        if (digits.length > 10) {
            variants.add(digits.takeLast(10))
        }

        if (!digits.startsWith("+") && digits.length >= 10) {
            variants.add("+91${digits.takeLast(10)}")
        }

        return variants.toList()
    }
}
