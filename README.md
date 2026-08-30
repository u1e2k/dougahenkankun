# 動画変換くん (DougaHenkanKun)

Androidネイティブ（Kotlin / Jetpack Compose）で動作する、ハードウェアアクセラレーションを活用した**高速動画圧縮＆圧縮後容量予測アプリ**です。

---

## 🌟 主な機能

1. **Photo Picker によるセキュアな動画選択**
   - 不要な広域ストレージ権限（READ_EXTERNAL_STORAGE等）を要求せず、最新の Android Photo Picker で動画を取得。
   - 解像度、再生時間、元ファイルサイズ、ビットレートを瞬時に解析・表示。

2. **リアルタイム容量予測プレビュー**
   - 圧縮実行前に、指定した解像度・ビットレート・コーデックから圧縮後の予想サイズと削減率（%）をリアルタイム計算。
   - 計算式: `予想バイト数 = ((映像bps + 音声bps) * 秒数 / 8) * 1.02`

3. **充実した圧縮パラメータ設定**
   - **解像度プリセット**: 原寸維持 / 1080p (FHD) / 720p (HD) / 480p (SD)
   - **コーデック選択**: H.264 (互換性重視) / H.265 (HEVC - 高圧縮)
   - **ビットレート調整**: プリセット（高画質 / 標準 / 軽量）およびスライダー調整
   - **目標容量逆算モード**: 「25MB以下に収める」などの指定時に、再生時間から必要な映像ビットレートを自動逆算。

4. **Media3 Transformer による高速ハードウェアエンコード**
   - AndroidX Media3 Transformer API を使用した GPU / MediaCodec ハードウェアアクセラレーション処理。
   - `Foreground Service` によるバックグラウンド長時間エンコード対応（通知バーに進捗バー表示）。
   - 完了時に `MediaStore.Video` API を通じて `Movies/DougaHenkanKun` へ安全に保存。

---

## 🛠 技術スタック

- **Language**: Kotlin 1.9
- **UI Framework**: Jetpack Compose, Material 3
- **Video Processing**: AndroidX Media3 Transformer (`1.3.1`), Media3 Effect
- **Architecture**: MVVM (ViewModel, StateFlow, Coroutines)
- **Background Processing**: Android Foreground Service (`mediaProcessing` / `dataSync`)
- **Storage**: MediaStore API, Photo Picker API (minSdk 26 / targetSdk 34)

---

## 📁 ディレクトリ構造

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/dougahenkankun/
│   ├── MainActivity.kt                  # エントリポイント & 権限ハンドリング
│   ├── core/
│   │   └── VideoSizeEstimator.kt        # メタデータ抽出、容量予測・逆算ロジック
│   ├── model/
│   │   └── VideoModels.kt               # データクラス、Enum定義
│   ├── service/
│   │   └── VideoCompressService.kt      # Media3 Transformer & Foreground Service
│   └── ui/
│       ├── MainViewModel.kt             # UI状態管理 & サービス連携
│       ├── screens/
│       │   └── CompressScreen.kt        # Jetpack Compose UI
│       └── theme/                       # Material 3 テーマ (Color, Type, Theme)
└── res/                                 # 文字列・テーマ・XML設定
```