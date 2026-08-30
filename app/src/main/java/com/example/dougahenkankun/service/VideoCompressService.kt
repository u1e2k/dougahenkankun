package com.example.dougahenkankun.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Codec
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.example.dougahenkankun.MainActivity
import com.example.dougahenkankun.R
import com.example.dougahenkankun.core.VideoSizeEstimator
import com.example.dougahenkankun.model.CompressionConfig
import com.example.dougahenkankun.model.CompressionStatus
import com.example.dougahenkankun.model.ResolutionPreset
import com.example.dougahenkankun.model.VideoCodec
import com.example.dougahenkankun.model.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class VideoCompressService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressPollingJob: Job? = null

    private var activeTransformer: Transformer? = null
    private var tempOutputFile: File? = null

    private val _compressionStatus = MutableStateFlow<CompressionStatus>(CompressionStatus.Idle)
    val compressionStatus: StateFlow<CompressionStatus> = _compressionStatus.asStateFlow()

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    inner class LocalBinder : Binder() {
        fun getService(): VideoCompressService = this@VideoCompressService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COMPRESSION -> {
                // 通常はバインド経由で startCompression が呼ばれるが、Intent経由起動も受け付け
            }
            ACTION_CANCEL_COMPRESSION -> {
                cancelCompression()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 圧縮処理の開始
     */
    fun startCompression(videoInfo: VideoInfo, config: CompressionConfig) {
        if (_compressionStatus.value is CompressionStatus.Compressing) {
            Log.w(TAG, "Compression is already in progress.")
            return
        }

        // Foreground Service 開始 (通知表示)
        startForeground(NOTIFICATION_ID, buildProgressNotification(0, "圧縮の準備中..."))

        serviceScope.launch {
            _compressionStatus.value = CompressionStatus.Compressing(0, "エンコード初期化中...")
            executeCompression(videoInfo, config)
        }
    }

    /**
     * Media3 Transformer によるハードウェアエンコード実行
     */
    private suspend fun executeCompression(videoInfo: VideoInfo, config: CompressionConfig) {
        val startTime = System.currentTimeMillis()
        try {
            // 一時出力ファイル
            val outputDir = cacheDir
            val tempFile = File(outputDir, "compressed_${System.currentTimeMillis()}.mp4")
            tempOutputFile = tempFile

            // 解像度ターゲット算出
            val (targetW, targetH) = config.resolutionPreset.calculateTargetDimensions(
                videoInfo.displayWidth,
                videoInfo.displayHeight
            )

            // Presentation エフェクト（解像度スケーリング & アスペクト比維持）
            val effectsList = mutableListOf<androidx.media3.common.Effect>()
            if (config.resolutionPreset != ResolutionPreset.ORIGINAL) {
                effectsList.add(
                    Presentation.createForWidthAndHeight(
                        targetW,
                        targetH,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            }

            // EditedMediaItem 構築
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(videoInfo.uri))
                .setEffects(Effects(emptyList(), effectsList))
                .build()

            // エンコーダ設定（ビットレート・コーデック）
            val encoderFactory = DefaultEncoderFactory.Builder(this@VideoCompressService)
                .setRequestedVideoEncoderSettings(
                    androidx.media3.transformer.VideoEncoderSettings.Builder()
                        .setBitrate(config.videoBitrateBps)
                        .build()
                )
                .setEnableFallback(true) // ハードウェアエンコーダ非対応時の自動フォールバック
                .build()

            // Transformer リスナー
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    Log.d(TAG, "Transformation completed successfully.")
                    stopProgressPolling()
                    serviceScope.launch {
                        handleCompressionSuccess(videoInfo, tempFile, startTime)
                    }
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    Log.e(TAG, "Transformation error: ${exportException.message}", exportException)
                    stopProgressPolling()
                    handleCompressionError("動画の圧縮処理中にエラーが発生しました: ${exportException.localizedMessage}", exportException)
                }

                override fun onFallbackApplied(
                    composition: androidx.media3.transformer.Composition,
                    originalTransformationRequest: TransformationRequest,
                    fallbackTransformationRequest: TransformationRequest
                ) {
                    Log.w(TAG, "Fallback applied: $fallbackTransformationRequest")
                }
            }

            // 出力MIMEタイプ決定 (H.264 / H.265)
            val outputVideoMimeType = when (config.codec) {
                VideoCodec.H264 -> MimeTypes.VIDEO_H264
                VideoCodec.H265 -> MimeTypes.VIDEO_H265
            }

            val transformer = Transformer.Builder(this@VideoCompressService)
                .setVideoMimeType(outputVideoMimeType)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()

            activeTransformer = transformer

            // 変換開始
            transformer.start(editedMediaItem, tempFile.absolutePath)

            // 進捗ポーリング開始
            startProgressPolling(transformer)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start transformer", e)
            handleCompressionError("エンコード開始に失敗しました: ${e.localizedMessage}", e)
        }
    }

    /**
     * 進捗ポーリング（Media3 Transformer から進捗取得）
     * Transformer のメソッドは全てメインスレッドからアクセスする必要がある
     */
    private fun startProgressPolling(transformer: Transformer) {
        progressPollingJob?.cancel()
        val progressHolder = ProgressHolder()

        progressPollingJob = serviceScope.launch(Dispatchers.Main) {
            while (isActive) {
                try {
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val progress = progressHolder.progress.coerceIn(0, 100)
                        _compressionStatus.value = CompressionStatus.Compressing(
                            progressPercent = progress,
                            currentStep = "ハードウェア圧縮中 ($progress%)"
                        )
                        updateProgressNotification(progress, "圧縮中: $progress%")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get progress: ${e.message}")
                }
                delay(300)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = null
    }

    /**
     * 圧縮完了時の処理：MediaStore への保存
     */
    private suspend fun handleCompressionSuccess(
        originalVideo: VideoInfo,
        tempFile: File,
        startTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val totalDurationMs = System.currentTimeMillis() - startTime
            val compressedBytes = tempFile.length()

            // MediaStore への保存
            val savedUri = saveToMediaStore(tempFile, originalVideo.fileName)

            // テンポラリファイル削除
            tempFile.delete()
            tempOutputFile = null
            activeTransformer = null

            withContext(Dispatchers.Main) {
                _compressionStatus.value = CompressionStatus.Success(
                    outputUri = savedUri,
                    originalBytes = originalVideo.sizeBytes,
                    compressedBytes = compressedBytes,
                    durationMs = totalDurationMs
                )
                showSuccessNotification(savedUri, originalVideo.sizeBytes, compressedBytes)
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save compressed video to MediaStore", e)
            handleCompressionError("保存処理中にエラーが発生しました: ${e.localizedMessage}", e)
        }
    }

    /**
     * MediaStore API を用いて Movies/DougaHenkanKun ディレクトリへ安全に保存
     */
    private fun saveToMediaStore(sourceFile: File, originalFileName: String): Uri {
        val baseName = originalFileName.substringBeforeLast(".")
        val newFileName = "${baseName}_compressed_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, newFileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DougaHenkanKun")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val destinationUri = contentResolver.insert(collectionUri, values)
            ?: throw IllegalStateException("MediaStore へのエントリ作成に失敗しました")

        contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
            FileInputStream(sourceFile).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("出力ストリームを開けませんでした")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(destinationUri, values, null, null)
        }

        return destinationUri
    }

    /**
     * エラー発生時の処理
     */
    private fun handleCompressionError(message: String, throwable: Throwable? = null) {
        stopProgressPolling()
        cleanUpTempFile()
        activeTransformer = null

        serviceScope.launch(Dispatchers.Main) {
            _compressionStatus.value = CompressionStatus.Error(message, throwable)
            showErrorNotification(message)
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    /**
     * 圧縮処理のキャンセル
     */
    fun cancelCompression() {
        stopProgressPolling()
        activeTransformer?.cancel()
        activeTransformer = null
        cleanUpTempFile()

        _compressionStatus.value = CompressionStatus.Cancelled
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanUpTempFile() {
        try {
            tempOutputFile?.let {
                if (it.exists()) it.delete()
            }
        } catch (_: Exception) {
        }
        tempOutputFile = null
    }

    // --- Notification 管理 ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildProgressNotification(progress: Int, contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VideoCompressService::class.java).apply {
            action = ACTION_CANCEL_COMPRESSION
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("動画を圧縮中...")
            .setContentText(contentText)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "キャンセル", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun updateProgressNotification(progress: Int, contentText: String) {
        val notification = buildProgressNotification(progress, contentText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showSuccessNotification(outputUri: Uri, originalBytes: Long, compressedBytes: Long) {
        val openVideoIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(outputUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 2, openVideoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val origStr = VideoSizeEstimator.formatFileSize(originalBytes)
        val compStr = VideoSizeEstimator.formatFileSize(compressedBytes)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("動画の圧縮が完了しました！")
            .setContentText("$origStr → $compStr に圧縮成功")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(SUCCESS_NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification(errorMessage: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("動画の圧縮に失敗しました")
            .setContentText(errorMessage)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(ERROR_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressPolling()
        cleanUpTempFile()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "VideoCompressService"
        const val CHANNEL_ID = "video_compress_channel"
        const val NOTIFICATION_ID = 1001
        const val SUCCESS_NOTIFICATION_ID = 1002
        const val ERROR_NOTIFICATION_ID = 1003

        const val ACTION_START_COMPRESSION = "com.example.dougahenkankun.ACTION_START"
        const val ACTION_CANCEL_COMPRESSION = "com.example.dougahenkankun.ACTION_CANCEL"
    }
}
