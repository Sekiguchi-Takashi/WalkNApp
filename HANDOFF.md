# HANDOFF — WalkNApp

## 現在地
- v0.5: マーカー描画不具合の修正
  - 原因: BitmapDrawable(null, bitmap) で Resources を null にしていたため描画されず
  - 対策: osmdroid 標準マーカーアイコンを複製し PorterDuff.SRC_IN でレアリティ色に着色
  - あわせて infoWindow を null 化（bonuspack レイアウト依存の回避）
- v0.4: 決定論スポーン＋マーカー表示（開発ステップ3完了）
  - SpawnEngine: 0.0015度グリッドセル（約166m角）＋日付シードで配置決定。geohash の代わりに整数グリッドを採用（隣接セル算出が単純なため）
  - 自セル＋隣接8セルを表示。セル移動時のみ再計算
  - ItemCatalog: 18種、レアリティ重み COMMON60/UNCOMMON25/RARE10/EPIC4/LEGENDARY1
  - マーカー色＝レアリティ。タップでアイテム名・レアリティ・距離を表示（取得はv0.5）
- v0.3: 固定 keystore (app/walkn-debug.keystore) で署名統一。以降アンインストール不要で上書き更新可能
- v0.2: osmdroid 地図表示＋現在地追従（開発ステップ2完了）
  - 位置情報パーミッション要求 → MAPNIK タイル表示 → MyLocationNewOverlay で現在地追従
  - 「現在地」ボタンで追従復帰

## 次のステップ（WALK_APP_SPEC.md §8）

4. 取得判定＋Inventory/Room 保存
5. セッション記録
6. インベントリ/図鑑/履歴画面
7. Health Connect 連携
8. 不正対策・エクスポート

## 規約
- ビルド: GitHub Actions（gradle 8.9 / AGP 8.5.2 / Kotlin 2.0.20 / compileSdk 35）
- APK は Actions の artifact "WalkNApp-debug" から取得
- NFT/外部連携コードは Phase 2 まで書かない（SPEC §7 の布石のみ守る）
