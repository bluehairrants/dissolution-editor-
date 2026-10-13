package com.dissolution.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Effect
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@UnstableApi
object MediaExport {
    suspend fun publishToMovies(context: Context, exportedFile: File): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, exportedFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Dissolution Editor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: error("Could not add export to Movies")
        try {
            val destination = context.contentResolver.openOutputStream(uri, "w") ?: error("Could not open export in Movies")
            exportedFile.inputStream().use { input -> destination.use(input::copyTo) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
            }
            exportedFile.delete()
            uri
        } catch (exception: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw exception
        }
    }

    fun export(
        context: Context,
        source: File,
        startMs: Long,
        endMs: Long,
        outputSize: Pair<Int, Int>?,
        videoMimeType: String,
        sourceFrameRate: Int,
        targetFrameRate: Int,
        targetBitrate: Int,
        onFinished: (Result<File>) -> Unit,
    ): Transformer {
        require(source.isFile) { "Source video is missing" }
        require(startMs >= 0 && endMs > startMs) { "Choose a valid trim range" }
        require(targetBitrate in 1_000_000..150_000_000) { "Bitrate must be between 1 and 150 Mbps" }
        require(targetFrameRate > 0 && targetFrameRate <= sourceFrameRate) {
            "Output frame rate cannot exceed the source frame rate"
        }

        val outputDirectory = File(context.getExternalFilesDir("Movies"), "exports")
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) { "Cannot create export directory" }
        val output = File(outputDirectory, "dissolution_${System.currentTimeMillis()}.mp4")
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(source))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build(),
            )
            .build()
        val effects = buildList<Effect> {
            if (outputSize != null) {
                add(Presentation.createForWidthAndHeight(outputSize.first, outputSize.second, Presentation.LAYOUT_SCALE_TO_FIT))
            }
            if (targetFrameRate < sourceFrameRate) {
                add(FrameDropEffect.createSimpleFrameDropEffect(sourceFrameRate.toFloat(), targetFrameRate.toFloat()))
            }
        }
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrate)
                    .build(),
            )
            .setEnableFallback(false)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(videoMimeType)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    onFinished(Result.success(output))
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    output.delete()
                    onFinished(Result.failure(exportException))
                }
            })
            .build()

        transformer.start(
            EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), effects))
                .build(),
            output.absolutePath,
        )
        return transformer
    }
}