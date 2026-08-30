package com.example.dougahenkankun.core

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.dougahenkankun.model.CompressionConfig
import com.example.dougahenkankun.model.EstimationResult
import com.example.dougahenkankun.model.QualityPreset
import com.example.dougahenkankun.model.ResolutionPreset
import com.example.dougahenkankun.model.VideoCodec
import com.example.dougahenkankun.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object VideoSizeEstimator {

    private const val CONTAINER_OVERHEAD_FACTOR = 1.02 // MP4コンテナ等のオーバーヘッド (+2%)
    private const val DEFAULT_AUDIO_BITRATE_BPS = 128_000 // 128 kbps
    private const val MIN_VIDEO_BITRATE_BPS = 150_000 // 150 kbps (最低保証ビットレート)

    /**
     * Uriから動画のメタデータを安全かつ瞬時に取得する
     */
    suspend fun extractVideoInfo(context: Context, videoUri: Uri): Result<VideoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)

                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                val mimeTypeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"

                val width = widthStr?.toIntOrNull() ?: 1920
                val height = heightStr?.toIntOrNull() ?: 1080
                val rotation = rotationStr?.toIntOrNull() ?: 0
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                val bitrate = bitrateStr?.toIntOrNull() ?: 0

                // ファイル名とファイルサイズを ContentResolver から取得
                val (fileName, fileSize) = queryFileInfo(context, videoUri)

                VideoInfo(
                    uri = videoUri,
                    fileName = fileName,
                    width = width,
                    height = height,
                    rotation = rotation,
                    durationMs = durationMs,
                    sizeBytes = fileSize,
                    videoBitrateBps = if (bitrate > 0) bitrate else estimateOriginalBitrate(fileSize, durationMs),
                    audioBitrateBps = DEFAULT_AUDIO_BITRATE_BPS,
                    mimeType = mimeTypeStr
                )
            } finally {
                retriever.release()
            }
        }
    }

    /**
     * ContentResolverからファイル名とサイズを照会
     */
    private fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "video.mp4"
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: "video.mp4"
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        // カーソルからサイズが取れなかった場合のフォールバック
        if (size <= 0) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.statSize
                }
            } catch (_: Exception) {
            }
        }

        return Pair(name, size)
    }

    /**
     * 元動画のファイルサイズと再生時間から概算ビットレートを算出
     */
    private fun estimateOriginalBitrate(fileSizeBytes: Long, durationMs: Long): Int {
        if (durationMs <= 0 || fileSizeBytes <= 0) return 3_000_000
        val durationSeconds = durationMs / 1000.0
        val totalBps = ((fileSizeBytes / CONTAINER_OVERHEAD_FACTOR) * 8 / durationSeconds).toInt()
        return max(MIN_VIDEO_BITRATE_BPS, totalBps - DEFAULT_AUDIO_BITRATE_BPS)
    }

    /**
     * 解像度・コーデック・画質プリセットに基づいた推奨ビットレート（bps）を計算
     */
    fun calculateRecommendedVideoBitrate(
        videoInfo: VideoInfo,
        resolutionPreset: ResolutionPreset,
        codec: VideoCodec,
        qualityPreset: QualityPreset
    ): Int {
        val (targetW, targetH) = resolutionPreset.calculateTargetDimensions(videoInfo.displayWidth, videoInfo.displayHeight)
        val pixelCount = targetW * targetH

        // 解像度ごとの基準ビットレート (H.264 / 30fps 基準)
        val baseBitrateBps: Double = when {
            pixelCount >= 3840 * 2160 -> 15_000_000.0 // 4K
            pixelCount >= 1920 * 1080 -> 4_500_000.0  // 1080p
            pixelCount >= 1280 * 720 -> 2_500_000.0   // 720p
            pixelCount >= 854 * 480 -> 1_200_000.0    // 480p
            else -> 600_000.0                        // 360p以下
        }

        // コーデックによる係数 (HEVC/H.265は同画質なら約35〜40%ビットレート削減可能)
        val codecFactor = when (codec) {
            VideoCodec.H264 -> 1.0
            VideoCodec.H265 -> 0.62
        }

        // 画質プリセットによる係数
        val qualityFactor = qualityPreset.multiplier

        val calculated = (baseBitrateBps * codecFactor * qualityFactor).toInt()
        // 元動画のビットレートより大きくならないように上限を設定 (品質劣化防止)
        val maxAllowed = if (videoInfo.videoBitrateBps > 0) (videoInfo.videoBitrateBps * 0.95).toInt() else calculated
        return calculated.coerceIn(MIN_VIDEO_BITRATE_BPS, max(MIN_VIDEO_BITRATE_BPS, maxAllowed))
    }

    /**
     * 【主要要件②】圧縮後容量の事前予測（リアルタイムプレビュー用）
     * 計算式: 予想バイト数 = ((映像bps + 音声bps) * 秒数 / 8) * 1.02
     */
    fun estimateCompressedSize(
        videoInfo: VideoInfo,
        config: CompressionConfig
    ): EstimationResult {
        val durationSec = videoInfo.durationSeconds
        val videoBps = config.videoBitrateBps
        val audioBps = config.audioBitrateBps

        // 計算式適用
        val estimatedBytes = (((videoBps + audioBps) * durationSec / 8.0) * CONTAINER_OVERHEAD_FACTOR).roundToLong()

        val originalBytes = videoInfo.sizeBytes
        val reductionPct = if (originalBytes > 0) {
            val pct = ((originalBytes - estimatedBytes).toFloat() / originalBytes.toFloat()) * 100f
            max(0f, pct)
        } else {
            0f
        }

        return EstimationResult(
            originalSizeBytes = originalBytes,
            estimatedSizeBytes = max(1024L, estimatedBytes),
            reductionPercentage = reductionPct,
            videoBitrateBps = videoBps,
            audioBitrateBps = audioBps,
            estimatedDurationSeconds = durationSec
        )
    }

    /**
     * 【主要要件③】目標容量（例: 25MB以下）から必要な映像ビットレートを自動逆算
     */
    fun calculateBitrateForTargetSize(
        targetSizeBytes: Long,
        durationMs: Long,
        audioBitrateBps: Int = DEFAULT_AUDIO_BITRATE_BPS
    ): Int {
        if (durationMs <= 0 || targetSizeBytes <= 0) return MIN_VIDEO_BITRATE_BPS

        val durationSec = durationMs / 1000.0
        // 目標容量（オーバーヘッド考慮前）
        val rawTargetBytes = targetSizeBytes / CONTAINER_OVERHEAD_FACTOR
        // 目標総ビット数
        val totalBits = rawTargetBytes * 8.0
        // 許容される総bps
        val totalBps = (totalBits / durationSec).roundToInt()

        // 映像ビットレート = 総bps - 音声bps
        val requiredVideoBps = totalBps - audioBitrateBps
        return max(MIN_VIDEO_BITRATE_BPS, requiredVideoBps)
    }

    // --- 表示用フォーマットヘルパー群 ---

    /**
     * バイト数を分かりやすい単位 (KB, MB, GB) にフォーマット
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    /**
     * 再生時間（ミリ秒）を mm:ss または hh:mm:ss 形式にフォーマット
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * ビットレート（bps）を Mbps / kbps 形式にフォーマット
     */
    fun formatBitrate(bps: Int): String {
        return if (bps >= 1_000_000) {
            String.format(Locale.US, "%.2f Mbps", bps / 1_000_000.0)
        } else {
            String.format(Locale.US, "%d kbps", bps / 1000)
        }
    }
}
