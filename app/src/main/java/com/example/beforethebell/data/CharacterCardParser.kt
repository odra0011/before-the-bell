package com.example.beforethebell.data

import android.util.Base64
import com.example.beforethebell.model.CharacterCard
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object CharacterCardParser {

    fun parseJson(jsonString: String): CharacterCard {
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid character card JSON: ${e.message}")
        }

        // V2 specification
        if (json.optString("spec") == "chara_card_v2" && json.has("data")) {
            val d = json.getJSONObject("data")
            val name = d.optString("name", "Unknown Character").trim()
            val desc = d.optString("description", "").trim()
            val scen = d.optString("scenario", "").trim()
            val personality = d.optString("personality", "").trim()
            val firstMes = d.optString("first_mes", "").trim()
            val mesExample = d.optString("mes_example", "").trim()
            val creatorNotes = d.optString("creator_notes", "").trim()

            // Extract world foundations from character book / lorebook if present
            var worldFoundations = ""
            val charBook = d.optJSONObject("character_book")
            if (charBook != null) {
                val entries = charBook.optJSONArray("entries")
                if (entries != null) {
                    val sb = StringBuilder()
                    for (i in 0 until entries.length()) {
                        val entry = entries.optJSONObject(i)
                        val content = entry?.optString("content", entry.optString("text", "")) ?: ""
                        if (content.isNotBlank()) {
                            sb.append(content).append("\n\n")
                        }
                    }
                    worldFoundations = sb.toString().trim()
                }
            }

            return CharacterCard(
                id = "char_${System.currentTimeMillis()}_${(1000..9999).random()}",
                name = if (name.isNotBlank()) name else "Unknown Character",
                title = creatorNotes.lines().firstOrNull { it.isNotBlank() } ?: "",
                description = desc,
                personality = personality,
                scenario = scen,
                firstMessage = firstMes,
                mesExample = mesExample,
                creatorNotes = creatorNotes,
                sourceType = "sillytavern_v2",
                importedAt = System.currentTimeMillis().toString(),
                worldFoundations = worldFoundations,
                rawJson = jsonString
            )
        }

        // V1 specification / fallback
        if (json.has("name") || json.has("description")) {
            val name = json.optString("name", "Unknown Character").trim()
            val desc = json.optString("description", "").trim()
            val scen = json.optString("scenario", "").trim()
            val personality = json.optString("personality", "").trim()
            val firstMes = json.optString("first_mes", "").trim()
            val mesExample = json.optString("mes_example", "").trim()
            val creatorNotes = json.optString("creator_notes", "").trim()

            return CharacterCard(
                id = "char_${System.currentTimeMillis()}_${(1000..9999).random()}",
                name = if (name.isNotBlank()) name else "Unknown Character",
                title = json.optString("title", creatorNotes.lines().firstOrNull { it.isNotBlank() } ?: ""),
                description = desc,
                personality = personality,
                scenario = scen,
                firstMessage = firstMes,
                mesExample = mesExample,
                creatorNotes = creatorNotes,
                sourceType = "sillytavern_v1",
                importedAt = System.currentTimeMillis().toString(),
                worldFoundations = "",
                rawJson = jsonString
            )
        }

        throw IllegalArgumentException("Unrecognized character card format: missing required name or description fields")
    }

    fun parsePng(pngBytes: ByteArray): CharacterCard {
        val pngSig = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )

        if (pngBytes.size < 8 || !pngBytes.take(8).toByteArray().contentEquals(pngSig)) {
            throw IllegalArgumentException("Invalid PNG image: header signature mismatch")
        }

        var offset = 8
        var charaJsonString: String? = null

        while (offset + 8 <= pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            val type = String(pngBytes, offset + 4, 4, StandardCharsets.US_ASCII)
            offset += 8

            if (length < 0 || offset + length > pngBytes.size) {
                break
            }

            val chunkData = pngBytes.copyOfRange(offset, offset + length)
            offset += length
            // Skip 4-byte CRC
            offset += 4

            if (type == "tEXt") {
                val nullIdx = chunkData.indexOf(0)
                if (nullIdx != -1) {
                    val keyword = String(chunkData, 0, nullIdx, StandardCharsets.ISO_8859_1)
                    val text = String(chunkData, nullIdx + 1, chunkData.size - nullIdx - 1, StandardCharsets.ISO_8859_1)
                    if (keyword.equals("chara", ignoreCase = true) || keyword.equals("ccv3", ignoreCase = true)) {
                        charaJsonString = try {
                            String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8)
                        } catch (e: Exception) {
                            text
                        }
                    }
                }
            } else if (type == "iTXt") {
                val nullIdx1 = chunkData.indexOf(0)
                if (nullIdx1 != -1) {
                    val keyword = String(chunkData, 0, nullIdx1, StandardCharsets.UTF_8)
                    if (keyword.equals("chara", ignoreCase = true) || keyword.equals("ccv3", ignoreCase = true)) {
                        var cur = nullIdx1 + 3
                        val nullIdx2 = chunkData.indexOf(0, cur)
                        if (nullIdx2 != -1) {
                            cur = nullIdx2 + 1
                            val nullIdx3 = chunkData.indexOf(0, cur)
                            if (nullIdx3 != -1) {
                                cur = nullIdx3 + 1
                                val text = String(chunkData, cur, chunkData.size - cur, StandardCharsets.UTF_8)
                                charaJsonString = try {
                                    String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8)
                                } catch (e: Exception) {
                                    text
                                }
                            }
                        }
                    }
                }
            }

            if (type == "IEND") break
        }

        if (charaJsonString == null) {
            throw IllegalArgumentException("No SillyTavern character metadata found in PNG chunks")
        }

        val card = parseJson(charaJsonString)
        val base64Img = "data:image/png;base64," + Base64.encodeToString(pngBytes, Base64.NO_WRAP)
        return card.copy(avatarBase64 = base64Img)
    }

    private fun ByteArray.indexOf(target: Byte, startIndex: Int = 0): Int {
        for (i in startIndex until this.size) {
            if (this[i] == target) return i
        }
        return -1
    }
}
