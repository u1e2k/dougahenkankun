package com.example.dougahenkankun.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dougahenkankun.core.VideoSizeEstimator
import com.example.dougahenkankun.model.CompressionConfig
import com.example.dougahenkankun.model.CompressionStatus
import com.example.dougahenkankun.model.EstimationResult
import com.example.dougahenkankun.model.QualityPreset
import com.example.dougahenkankun.model.ResolutionPreset
import com.example.dougahenkankun.model.VideoCodec
import com.example.dougahenkankun.model.VideoInfo
import com.example.dougahenkankun.service.VideoCompressService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedVideo: VideoInfo? = null,
    val isLoadingVideo: Boolean = false,
    val loadError: String? = null,
    val config: CompressionConfig = CompressionConfig(),
    val estimation: EstimationResult? = null,
    val compressionStatus: CompressionStatus = CompressionStatus.Idle,
    val showTargetSizeDialog: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var compressService: VideoCompressService? = null
    private var isBound = false
    private var serviceStatusJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? VideoCompressService.LocalBinder
            compressService = localBinder?.getService()
            isBound = true

            // サービス側の圧縮ステータスを監視
            serviceStatusJob?.cancel()
            serviceStatusJob = viewModelScope.launch {
                compressService?.compressionStatus?.collectLatest { status ->
                    _uiState.update { it.copy(compressionStatus = status) }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            compressService = null
            isBound = false
            serviceStatusJob?.cancel()
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val intent = Intent(getApplication(), VideoCompressService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Photo Picker から選択された動画Uriをロード
     */
    fun onVideoSelected(uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVideo = true, loadError = null) }

            val result = VideoSizeEstimator.extractVideoInfo(getApplication(), uri)
            result.onSuccess { videoInfo ->
                // 初期推奨ビットレートの計算 (標準プリセット)
                val initialBitrate = VideoSizeEstimator.calculateRecommendedVideoBitrate(
                    videoInfo = videoInfo,
                    resolutionPreset = ResolutionPreset.ORIGINAL,
                    codec = VideoCodec.H264,
                    qualityPreset = QualityPreset.STANDARD
                )

                val initialConfig = CompressionConfig(
                    resolutionPreset = ResolutionPreset.ORIGINAL,
                    codec = VideoCodec.H264,
                    qualityPreset = QualityPreset.STANDARD,
                    videoBitrateBps = initialBitrate,
                    audioBitrateBps = 128_000
                )

                val estimation = VideoSizeEstimator.estimateCompressedSize(videoInfo, initialConfig)

                _uiState.update {
                    it.copy(
                        selectedVideo = videoInfo,
                        isLoadingVideo = false,
                        config = initialConfig,
                        estimation = estimation,
                        compressionStatus = CompressionStatus.Idle
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingVideo = false,
                        loadError = "動画情報の取得に失敗しました: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * 解像度プリセットの変更
     */
    fun onResolutionPresetChanged(preset: ResolutionPreset) {
        val currentVideo = _uiState.value.selectedVideo ?: return
        val currentConfig = _uiState.value.config

        val newBitrate = if (currentConfig.qualityPreset != QualityPreset.CUSTOM) {
            VideoSizeEstimator.calculateRecommendedVideoBitrate(
                videoInfo = currentVideo,
                resolutionPreset = preset,
                codec = currentConfig.codec,
                qualityPreset = currentConfig.qualityPreset
            )
        } else {
            currentConfig.videoBitrateBps
        }

        val updatedConfig = currentConfig.copy(
            resolutionPreset = preset,
            videoBitrateBps = newBitrate
        )

        recalculateEstimation(currentVideo, updatedConfig)
    }

    /**
     * コーデックの変更 (H.264 / H.265)
     */
    fun onCodecChanged(codec: VideoCodec) {
        val currentVideo = _uiState.value.selectedVideo ?: return
        val currentConfig = _uiState.value.config

        val newBitrate = if (currentConfig.qualityPreset != QualityPreset.CUSTOM) {
            VideoSizeEstimator.calculateRecommendedVideoBitrate(
                videoInfo = currentVideo,
                resolutionPreset = currentConfig.resolutionPreset,
                codec = codec,
                qualityPreset = currentConfig.qualityPreset
            )
        } else {
            currentConfig.videoBitrateBps
        }

        val updatedConfig = currentConfig.copy(
            codec = codec,
            videoBitrateBps = newBitrate
        )

        recalculateEstimation(currentVideo, updatedConfig)
    }

    /**
     * 画質プリセットの変更 (高画質 / 標準 / 軽量)
     */
    fun onQualityPresetChanged(preset: QualityPreset) {
        val currentVideo = _uiState.value.selectedVideo ?: return
        val currentConfig = _uiState.value.config

        val newBitrate = VideoSizeEstimator.calculateRecommendedVideoBitrate(
            videoInfo = currentVideo,
            resolutionPreset = currentConfig.resolutionPreset,
            codec = currentConfig.codec,
            qualityPreset = preset
        )

        val updatedConfig = currentConfig.copy(
            qualityPreset = preset,
            videoBitrateBps = newBitrate
        )

        recalculateEstimation(currentVideo, updatedConfig)
    }

    /**
     * ビットレートの手動スライダー変更
     */
    fun onVideoBitrateChanged(bitrateBps: Int) {
        val currentVideo = _uiState.value.selectedVideo ?: return
        val currentConfig = _uiState.value.config

        val updatedConfig = currentConfig.copy(
            videoBitrateBps = bitrateBps,
            qualityPreset = QualityPreset.CUSTOM,
            targetMaxSizeBytes = null
        )

        recalculateEstimation(currentVideo, updatedConfig)
    }

    /**
     * 目標容量（MB）からの逆算適用
     */
    fun applyTargetSizeMb(targetMb: Float) {
        val currentVideo = _uiState.value.selectedVideo ?: return
        val currentConfig = _uiState.value.config

        val targetSizeBytes = (targetMb * 1024 * 1024).toLong()
        val calculatedBitrate = VideoSizeEstimator.calculateBitrateForTargetSize(
            targetSizeBytes = targetSizeBytes,
            durationMs = currentVideo.durationMs,
            audioBitrateBps = currentConfig.audioBitrateBps
        )

        val updatedConfig = currentConfig.copy(
            videoBitrateBps = calculatedBitrate,
            qualityPreset = QualityPreset.CUSTOM,
            targetMaxSizeBytes = targetSizeBytes
        )

        _uiState.update { it.copy(showTargetSizeDialog = false) }
        recalculateEstimation(currentVideo, updatedConfig)
    }

    fun setTargetSizeDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showTargetSizeDialog = visible) }
    }

    /**
     * 容量予測のリアルタイム再計算
     */
    private fun recalculateEstimation(videoInfo: VideoInfo, config: CompressionConfig) {
        val estimation = VideoSizeEstimator.estimateCompressedSize(videoInfo, config)
        _uiState.update {
            it.copy(
                config = config,
                estimation = estimation
            )
        }
    }

    /**
     * 圧縮処理の開始リクエスト
     */
    fun startCompression() {
        val videoInfo = _uiState.value.selectedVideo ?: return
        val config = _uiState.value.config

        compressService?.startCompression(videoInfo, config)
    }

    /**
     * 圧縮処理のキャンセルリクエスト
     */
    fun cancelCompression() {
        compressService?.cancelCompression()
    }

    /**
     * 完了ダイアログの終了 / リセット
     */
    fun resetStatus() {
        _uiState.update { it.copy(compressionStatus = CompressionStatus.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            isBound = false
        }
    }
}
