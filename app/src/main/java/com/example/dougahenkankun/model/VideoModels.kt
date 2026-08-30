package com.example.dougahenkankun.model

import android.net.Uri
import androidx.media3.common.MimeTypes

/**
 * 選択された動画の基本情報
 */
data class VideoInfo(
    val uri: Uri,
    val fileName: String,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val durationMs: Long,
    val sizeBytes: Long,
    val videoBitrateBps: Int = 0,
    val audioBitrateBps: Int = 128_000,
    val mimeType: String = "video/mp4"
) {
    val durationSeconds: Double
        get() = (durationMs / 1000.0).coerceAtLeast(0.1)

    // 回転を考慮した表示上の幅と高さ
    val displayWidth: Int
        get() = if (rotation == 90 || rotation == 270) height else width

    val displayHeight: Int
        get() = if (rotation == 90 || rotation == 270) width else height
}

/**
 * 解像度プリセット定義
 */
enum class ResolutionPreset(val label: String, val targetShortSide: Int?) {
    ORIGINAL("原寸維持", null),
    P1080("1080p (FHD)", 1080),
    P720("720p (HD)", 720),
    P480("480p (SD)", 480);

    /**
     * 元の解像度とアスペクト比を維持しつつ、短辺/長辺をスケーリングした幅・高さを算出
     */
    fun calculateTargetDimensions(srcWidth: Int, srcHeight: Int): Pair<Int, Int> {
        val target = targetShortSide ?: return Pair(srcWidth, srcHeight)
        val isPortrait = srcHeight > srcWidth
        val shortSide = if (isPortrait) srcWidth else srcHeight
        val longSide = if (isPortrait) srcHeight else srcWidth

        if (shortSide <= target) {
            // 元が目標解像度以下の場合は拡大せず原寸維持
            return Pair(srcWidth, srcHeight)
        }

        val scale = target.toFloat() / shortSide.toFloat()
        val newShort = (shortSide * scale).toInt()
        val newLong = (longSide * scale).toInt()

        // 偶数（エンコーダの要求）に丸める
        val finalShort = newShort + (newShort % 2)
        val finalLong = newLong + (newLong % 2)

        return if (isPortrait) {
            Pair(finalShort, finalLong)
        } else {
            Pair(finalLong, finalShort)
        }
    }
}

/**
 * 動画コーデック選択
 */
enum class VideoCodec(val label: String, val mimeType: String, val description: String) {
    H264("H.264 / AVC", MimeTypes.VIDEO_H264, "互換性重視 (SNS・Web・旧端末向け)"),
    H265("H.265 / HEVC", MimeTypes.VIDEO_H265, "高圧縮 (高品質かつ省容量)");
}

/**
 * 画質プリセット（目標ビットレート基準）
 */
enum class QualityPreset(val label: String, val multiplier: Float) {
    HIGH("高画質", 1.2f),
    STANDARD("標準", 0.7f),
    LOW("軽量 (節約)", 0.35f),
    CUSTOM("カスタム", 1.0f);
}

/**
 * 圧縮実行設定
 */
data class CompressionConfig(
    val resolutionPreset: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val codec: VideoCodec = VideoCodec.H264,
    val qualityPreset: QualityPreset = QualityPreset.STANDARD,
    val videoBitrateBps: Int = 2_500_000, // 2.5 Mbps
    val audioBitrateBps: Int = 128_000,  // 128 kbps
    val targetMaxSizeBytes: Long? = null // 目標容量指定（逆算用）
)

/**
 * 容量事前予測の結果データ
 */
data class EstimationResult(
    val originalSizeBytes: Long,
    val estimatedSizeBytes: Long,
    val reductionPercentage: Float, // 削減率 (%: 例 45.5)
    val videoBitrateBps: Int,
    val audioBitrateBps: Int,
    val estimatedDurationSeconds: Double
)

/**
 * 圧縮処理の状態
 */
sealed interface CompressionStatus {
    data object Idle : CompressionStatus
    data object Estimating : CompressionStatus
    data class Compressing(val progressPercent: Int, val currentStep: String = "エンコード中...") : CompressionStatus
    data class Success(
        val outputUri: Uri,
        val originalBytes: Long,
        val compressedBytes: Long,
        val durationMs: Long
    ) : CompressionStatus
    data class Error(val message: String, val throwable: Throwable? = null) : CompressionStatus
    data object Cancelled : CompressionStatus
}
