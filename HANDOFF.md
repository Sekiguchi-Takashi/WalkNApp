# HANDOFF — WalkNApp

## 現在地
- v0.1: リポジトリ＋Actionsビルド環境（開発ステップ1完了）
- MainActivity はビルド確認用プレースホルダのみ

## 次のステップ（WALK_APP_SPEC.md §8）
2. 地図表示＋現在地（osmdroid — 依存は導入済み）
3. 決定論スポーン＋マーカー表示
4. 取得判定＋Inventory/Room 保存
5. セッション記録
6. インベントリ/図鑑/履歴画面
7. Health Connect 連携
8. 不正対策・エクスポート

## 規約
- ビルド: GitHub Actions（gradle 8.9 / AGP 8.5.2 / Kotlin 2.0.20 / compileSdk 35）
- APK は Actions の artifact "WalkNApp-debug" から取得
- NFT/外部連携コードは Phase 2 まで書かない（SPEC §7 の布石のみ守る）
