# HANDOFF — WalkNApp

## 現在地
- v0.2: osmdroid 地図表示＋現在地追従（開発ステップ2完了）
  - 位置情報パーミッション要求 → MAPNIK タイル表示 → MyLocationNewOverlay で現在地追従
  - 「現在地」ボタンで追従復帰

## 次のステップ（WALK_APP_SPEC.md §8）
3. 決定論スポーン＋マーカー表示（geohash＋日付シード）
4. 取得判定＋Inventory/Room 保存
5. セッション記録
6. インベントリ/図鑑/履歴画面
7. Health Connect 連携
8. 不正対策・エクスポート

## 規約
- ビルド: GitHub Actions（gradle 8.9 / AGP 8.5.2 / Kotlin 2.0.20 / compileSdk 35）
- APK は Actions の artifact "WalkNApp-debug" から取得
- NFT/外部連携コードは Phase 2 まで書かない（SPEC §7 の布石のみ守る）
