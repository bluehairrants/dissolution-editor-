package com.dissolution.editor

import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.media3.common.MimeTypes
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private data class EncoderProfile(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val bitrate: Int,
)

data class VideoInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Int,
)

data class EncoderProbeConfig(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sourceFrameRate: Int,
    val bitrate: Int,
)

object VideoCapabilities {
    private val encoderProfiles = ConcurrentHashMap<EncoderProfile, Boolean>()

    fun inspect(file: File): VideoInfo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true) continue
                    val width = format.getInteger(MediaFormat.KEY_WIDTH)
                    val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    val frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        format.getInteger(MediaFormat.KEY_FRAME_RATE)
                    } else {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                            ?.toFloatOrNull()?.toInt() ?: 30
                    }
                    return VideoInfo(duration, width, height, frameRate.coerceAtLeast(1))
                }
            } finally {
                extractor.release()
            }
            error("No video track found")
        } finally {
            retriever.release()
        }
    }

    fun supports(
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Int,
        bitrate: Int,
    ): Boolean = encoderProfiles.computeIfAbsent(EncoderProfile(mimeType, width, height, frameRate, bitrate)) {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
                codec.isEncoder && codec.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } &&
                    runCatching {
                        val capabilities = codec.getCapabilitiesForType(mimeType)
                        val video = capabilities.videoCapabilities
                        video.areSizeAndRateSupported(width, height, frameRate.toDouble()) &&
                            video.bitrateRange.contains(bitrate)
                    }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }

    fun mimeType(isHevc: Boolean): String = if (isHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
}