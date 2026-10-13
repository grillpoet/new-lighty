package com.grillpoet.lighty.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioTagger {

    private const val MAX_COVER_BYTES = 500 * 1024

    suspend fun write(
        file: File,
        title: String,
        artist: String,
        lyric: String?,
        coverUrl: File?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists() || file.length() == 0L) error("文件不存在或为空")

            val audioFile = AudioFileIO.read(file)
            val tag: Tag = audioFile.tag ?: when {
                file.extension.equals("mp3", true) -> ID3v24Tag()
                else -> error("无法为 ${file.extension} 创建默认标签")
            }

            setSafe { tag.setField(FieldKey.TITLE, title) }
            setSafe { tag.setField(FieldKey.ARTIST, artist) }
            setSafe { tag.setField(FieldKey.ALBUM_ARTIST, artist) }

            if (!lyric.isNullOrBlank()) {
                setSafe { tag.setField(FieldKey.LYRICS, lyric) }
            }

            if (coverUrl != null && coverUrl.exists()) {
                attachArtwork(tag, coverUrl)
            }

            audioFile.tag = tag
            audioFile.commit()
        }
    }

    private inline fun setSafe(block: () -> Unit) {
        runCatching { block() }
    }

    private fun attachArtwork(tag: Tag, coverFile: File) {
        runCatching {
            val bytes = if (coverFile.length() > MAX_COVER_BYTES) {
                compressCover(coverFile) ?: coverFile.readBytes()
            } else {
                coverFile.readBytes()
            }

            // 先把压缩后的字节写回临时文件，让 ArtworkFactory 从文件读
            val tmp = File(coverFile.parentFile, "art_${coverFile.name}")
            tmp.writeBytes(bytes)

            val artwork = ArtworkFactory.createArtworkFromFile(tmp)
                ?: return@runCatching

            artwork.pictureType = 3  // Cover (front)
            artwork.mimeType = "image/jpeg"

            tag.deleteArtworkField()
            tag.addField(artwork)

            tmp.delete()
        }
    }

    private fun compressCover(coverFile: File): ByteArray? = runCatching {
        val bitmap = BitmapFactory.decodeFile(coverFile.absolutePath) ?: return null
        val out = ByteArrayOutputStream()
        var quality = 85
        var bytes: ByteArray
        do {
            out.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 15
        } while (bytes.size > MAX_COVER_BYTES && quality > 20)
        bitmap.recycle()
        bytes
    }.getOrNull()
}