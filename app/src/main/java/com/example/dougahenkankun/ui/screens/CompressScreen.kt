package com.example.dougahenkankun.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dougahenkankun.core.VideoSizeEstimator
import com.example.dougahenkankun.model.CompressionStatus
import com.example.dougahenkankun.model.QualityPreset
import com.example.dougahenkankun.model.ResolutionPreset
import com.example.dougahenkankun.model.VideoCodec
import com.example.dougahenkankun.model.VideoInfo
import com.example.dougahenkankun.ui.MainUiState
import com.example.dougahenkankun.ui.MainViewModel
import com.example.dougahenkankun.ui.theme.EmeraldGreen
import com.example.dougahenkankun.ui.theme.EmeraldLight
import com.example.dougahenkankun.ui.theme.ErrorRed
import com.example.dougahenkankun.ui.theme.PrimaryDarkIndigo
import com.example.dougahenkankun.ui.theme.PrimaryIndigo
import com.example.dougahenkankun.ui.theme.PrimaryLightIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(
    viewModel: MainViewModel,
    onRequireNotificationPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Photo Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.onVideoSelected(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "動画変換くん",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 動画選択カード / メディア情報
                VideoSelectorCard(
                    videoInfo = uiState.selectedVideo,
                    isLoading = uiState.isLoadingVideo,
                    error = uiState.loadError,
                    onPickVideo = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )

                // 動画が選択されている場合のみ設定・予測を表示
                if (uiState.selectedVideo != null) {
                    // 2. 容量事前予測プレビューバナー (要件②)
                    EstimationPreviewBanner(
                        originalBytes = uiState.selectedVideo!!.sizeBytes,
                        estimatedBytes = uiState.estimation?.estimatedSizeBytes ?: 0L,
                        reductionPercentage = uiState.estimation?.reductionPercentage ?: 0f
                    )

                    // 3. 圧縮パラメータ設定 (要件③)
                    CompressionSettingsCard(
                        uiState = uiState,
                        onResolutionSelected = viewModel::onResolutionPresetChanged,
                        onCodecSelected = viewModel::onCodecChanged,
                        onQualitySelected = viewModel::onQualityPresetChanged,
                        onBitrateChanged = viewModel::onVideoBitrateChanged,
                        onOpenTargetSizeDialog = { viewModel.setTargetSizeDialogVisible(true) }
                    )

                    // 4. アクションボタン / 進捗表示 (要件④)
                    ActionAndProgressCard(
                        compressionStatus = uiState.compressionStatus,
                        onStartCompression = {
                            onRequireNotificationPermission()
                            viewModel.startCompression()
                        },
                        onCancelCompression = viewModel::cancelCompression
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // 目標容量逆算ダイアログ
            if (uiState.showTargetSizeDialog) {
                TargetSizeCalculatorDialog(
                    currentSizeBytes = uiState.selectedVideo?.sizeBytes ?: 0L,
                    onDismiss = { viewModel.setTargetSizeDialogVisible(false) },
                    onApplyTargetSizeMb = viewModel::applyTargetSizeMb
                )
            }

            // 完了ダイアログ
            (uiState.compressionStatus as? CompressionStatus.Success)?.let { success ->
                SuccessResultDialog(
                    result = success,
                    onDismiss = viewModel::resetStatus,
                    onOpenVideo = {
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(success.outputUri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(openIntent, "動画を再生"))
                    }
                )
            }

            // エラーダイアログ
            (uiState.compressionStatus as? CompressionStatus.Error)?.let { error ->
                AlertDialog(
                    onDismissRequest = viewModel::resetStatus,
                    icon = { Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed) },
                    title = { Text("エラーが発生しました") },
                    text = { Text(error.message) },
                    confirmButton = {
                        Button(onClick = viewModel::resetStatus) {
                            Text("閉じる")
                        }
                    }
                )
            }
        }
    }
}

/**
 * ① 動画選択 & メタデータ表示カード
 */
@Composable
fun VideoSelectorCard(
    videoInfo: VideoInfo?,
    isLoading: Boolean,
    error: String?,
    onPickVideo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPickVideo),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text("動画の情報を読み込み中...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (videoInfo != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.VideoFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = videoInfo.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "タップして別の動画を選択",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // メタデータ3点表示 (解像度 / 長さ / サイズ)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetaItem(
                            label = "元の解像度",
                            value = "${videoInfo.displayWidth} × ${videoInfo.displayHeight}"
                        )
                        MetaDivider()
                        MetaItem(
                            label = "再生時間",
                            value = VideoSizeEstimator.formatDuration(videoInfo.durationMs)
                        )
                        MetaDivider()
                        MetaItem(
                            label = "元サイズ",
                            value = VideoSizeEstimator.formatFileSize(videoInfo.sizeBytes)
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "動画を選択してください",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "タップして端末内の動画を選択（Photo Picker）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (error != null) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun MetaDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    )
}

/**
 * ② 容量事前予測プレビューバナー
 */
@Composable
fun EstimationPreviewBanner(
    originalBytes: Long,
    estimatedBytes: Long,
    reductionPercentage: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(PrimaryIndigo, PrimaryDarkIndigo)
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "圧縮後の予想サイズ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    // 削減率バッジ
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldGreen
                    ) {
                        Text(
                            text = "約 ${reductionPercentage.toInt()}% 削減",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "元サイズ",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.7f))
                        )
                        Text(
                            text = VideoSizeEstimator.formatFileSize(originalBytes),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Text(
                        text = "➔",
                        style = MaterialTheme.typography.titleLarge.copy(color = EmeraldLight),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "予想サイズ",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.7f))
                        )
                        Text(
                            text = VideoSizeEstimator.formatFileSize(estimatedBytes),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * ③ 圧縮設定パネル
 */
@Composable
fun CompressionSettingsCard(
    uiState: MainUiState,
    onResolutionSelected: (ResolutionPreset) -> Unit,
    onCodecSelected: (VideoCodec) -> Unit,
    onQualitySelected: (QualityPreset) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onOpenTargetSizeDialog: () -> Unit
) {
    val config = uiState.config

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "圧縮設定",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            // 解像度プリセット
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "解像度",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(ResolutionPreset.entries.toTypedArray()) { preset ->
                        FilterChip(
                            selected = config.resolutionPreset == preset,
                            onClick = { onResolutionSelected(preset) },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // コーデック選択
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "エンコード方式 (コーデック)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VideoCodec.entries.forEach { codec ->
                        val isSelected = config.codec == codec
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
                                )
                                .clickable { onCodecSelected(codec) }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = codec.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = codec.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 画質プリセット & 目標容量逆算ボタン
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "画質 / ビットレート",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onOpenTargetSizeDialog,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("指定容量から逆算", fontSize = 13.sp)
                    }
                }

                // プリセットボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(QualityPreset.HIGH, QualityPreset.STANDARD, QualityPreset.LOW).forEach { preset ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = config.qualityPreset == preset,
                            onClick = { onQualitySelected(preset) },
                            label = {
                                Text(
                                    text = preset.label,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        )
                    }
                }

                // ビットレートスライダー
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "映像ビットレート: ${VideoSizeEstimator.formatBitrate(config.videoBitrateBps)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (config.qualityPreset == QualityPreset.CUSTOM) {
                            Text(
                                text = "手動調整中",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Slider(
                        value = config.videoBitrateBps.toFloat(),
                        onValueChange = { onBitrateChanged(it.toInt()) },
                        valueRange = 300_000f..15_000_000f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

/**
 * ④ 圧縮アクション & プログレスカード
 */
@Composable
fun ActionAndProgressCard(
    compressionStatus: CompressionStatus,
    onStartCompression: () -> Unit,
    onCancelCompression: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            when (compressionStatus) {
                is CompressionStatus.Compressing -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = compressionStatus.currentStep,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "${compressionStatus.progressPercent}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        LinearProgressIndicator(
                            progress = { compressionStatus.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )

                        OutlinedButton(
                            onClick = onCancelCompression,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("キャンセル")
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = onStartCompression,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ハードウェア圧縮を開始",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 目標容量逆算ダイアログ
 */
@Composable
fun TargetSizeCalculatorDialog(
    currentSizeBytes: Long,
    onDismiss: () -> Unit,
    onApplyTargetSizeMb: (Float) -> Unit
) {
    var textValue by remember { mutableStateOf("25") }
    val currentMb = currentSizeBytes / (1024f * 1024f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目標容量からビットレート逆算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "指定したファイルサイズ以下に収まるよう、必要な映像ビットレートを自動算出します。（Discordの25MB制限やメール添付等に便利です）",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "元サイズ: ${String.format("%.1f", currentMb)} MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("目標サイズ (MB)") },
                    suffix = { Text("MB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // よくあるプリセット
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("10", "25", "50", "100").forEach { mb ->
                        FilterChip(
                            selected = textValue == mb,
                            onClick = { textValue = mb },
                            label = { Text("${mb}MB") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetMb = textValue.toFloatOrNull() ?: 25f
                    onApplyTargetSizeMb(targetMb)
                }
            ) {
                Text("適用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * 成功結果ダイアログ
 */
@Composable
fun SuccessResultDialog(
    result: CompressionStatus.Success,
    onDismiss: () -> Unit,
    onOpenVideo: () -> Unit
) {
    val reductionPct = if (result.originalBytes > 0) {
        ((result.originalBytes - result.compressedBytes).toFloat() / result.originalBytes.toFloat()) * 100f
    } else {
        0f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "動画の圧縮が完了しました！",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ギャラリー（Movies/DougaHenkanKun）に安全に保存されました。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("元サイズ:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(VideoSizeEstimator.formatFileSize(result.originalBytes), fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("圧縮後サイズ:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        VideoSizeEstimator.formatFileSize(result.compressedBytes),
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("削減率:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${reductionPct.toInt()}% 削減", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("処理時間:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format("%.1f 秒", result.durationMs / 1000.0))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenVideo,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("動画を再生", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
