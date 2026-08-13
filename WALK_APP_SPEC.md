# WalkApp 仕様書

バージョン: 1.0 (Phase 1 — ウォーキングアプリ単体)
最終更新: 2026-08-14

---

## 1. コンセプト

歩くとマップ上にアイテムが出現し、近づいて拾えるウォーキングアプリ。
歩くこと自体に報酬（アイテム収集）を与え、継続動機をつくる。

- Phase 1: ローカル完結のウォーキング＋アイテム収集アプリ（本仕様書の範囲）
- Phase 2 (次ステップ・本仕様書対象外): NFTミント・外部アプリ連携

Phase 2 を見越し、アイテム個体には取得来歴（座標・日時・シリアル）を最初から持たせる。

---

## 2. オントロジー（Phase 1）

### 2.1 ユーザー系

| エンティティ | 属性 | 備考 |
|---|---|---|
| Walker | id, nickname, totalSteps, totalDistance, level | 端末内1ユーザー固定 |
| Inventory | walkerId, items[] | ItemInstance の集合 |

### 2.2 歩行系

| エンティティ | 属性 | 備考 |
|---|---|---|
| WalkSession | id, startAt, endAt, steps, distanceM, track[] | 1回の散歩記録 |
| StepRecord | timestamp, steps | Health Connect 由来 |
| ValidityCheck | sessionId, result, reason | 速度超過（車移動）を除外 |

### 2.3 マップ・出現系

| エンティティ | 属性 | 備考 |
|---|---|---|
| MapCell | geohash (精度7: 約150m角) | グリッド単位 |
| SpawnPoint | id, cellGeohash, lat, lng, itemDefId, spawnDate, expiresAt | 出現個体 |
| SpawnRule | 出現ロジック定義 | §5 参照 |

### 2.4 アイテム系

| エンティティ | 属性 | 備考 |
|---|---|---|
| ItemDefinition | id, name, category, rarity, imageRes, capabilities[] | アイテムの型。capabilities は Phase 2 用に空定義 |
| ItemInstance | id(serial), itemDefId, acquiredAt, acquiredLat, acquiredLng, sessionId | 拾った個体。来歴＝将来のNFTメタデータ |
| Rarity | COMMON / UNCOMMON / RARE / EPIC / LEGENDARY | 出現重み付き |
| ItemCategory | DECORATION / MATERIAL / TICKET / KEY | Phase 2 の用途を見越した分類 |

### 2.5 イベント系

| エンティティ | 属性 | 備考 |
|---|---|---|
| AcquisitionEvent | id, itemInstanceId, walkerId, at, lat, lng | 取得履歴（監査用） |

---

## 3. 画面構成

| 画面 | 内容 |
|---|---|
| マップ画面（メイン） | 現在地＋周辺の SpawnPoint 表示。30m 以内のアイテムはタップで取得。セッション開始/終了ボタン |
| インベントリ画面 | 取得アイテム一覧。レアリティ/カテゴリでフィルタ。個体詳細（取得場所・日時） |
| 履歴画面 | WalkSession 一覧（日付・歩数・距離・取得数）。簡易グラフ |
| 図鑑画面 | ItemDefinition 一覧。未取得はシルエット表示 |
| 設定画面 | ニックネーム、Health Connect 連携、データエクスポート(JSON) |

ナビゲーション: BottomNavigation 4タブ（マップ / インベントリ / 履歴 / 図鑑）＋設定はトップバー。

---

## 4. 技術構成

| 項目 | 選定 | 理由 |
|---|---|---|
| 言語/UI | Kotlin + Jetpack Compose | 既存アプリ群と統一 |
| 地図 | osmdroid | APIキー不要・無料・Actions ビルドに支障なし |
| 歩数 | Health Connect API | Android 14+ 標準。フォールバックに TYPE_STEP_COUNTER センサー |
| 位置 | FusedLocationProvider | 取得判定・トラック記録 |
| DB | Room | 全データローカル。サーバーレス |
| ビルド | GitHub Actions | 既存規約どおり。APK artifact 配布 |

必要パーミッション: ACCESS_FINE_LOCATION, ACTIVITY_RECOGNITION, Health Connect READ_STEPS。
バックグラウンド位置は Phase 1 では不要（セッション中のフォアグラウンドサービスのみ）。

---

## 5. 出現ロジック（サーバーレス設計）

決定論的スポーン方式: `seed = hash(cellGeohash + 日付)` で疑似乱数を初期化し、セルごとの出現有無・座標・アイテム種を決定する。

- サーバー不要で全端末が同じ配置を再現できる（将来のマルチユーザー化にもそのまま使える）
- 日替わりで配置が変わる
- 出現確率はレアリティ重みで制御（例: COMMON 60% / UNCOMMON 25% / RARE 10% / EPIC 4% / LEGENDARY 1%）
- 表示範囲: 現在地の geohash セル＋隣接8セル
- 取得済み SpawnPoint は端末ローカルで消し込み（acquiredSpawnIds テーブル）

歩行連動ボーナス: 当日歩数がしきい値（例: 3000歩/6000歩/10000歩）を超えるごとに、周辺セルの出現数を加算する。

### 不正対策（最低限）

- セッション中の移動速度が 15km/h 超の区間は距離・取得判定から除外
- Mock Location 検出時は取得無効

---

## 6. データモデル（Room 主要テーブル）

```
walker(id, nickname, totalSteps, totalDistanceM, level, createdAt)
walk_session(id, startAt, endAt, steps, distanceM, trackJson, valid)
item_definition(id, name, category, rarity, imageRes, capabilitiesJson)
item_instance(id, itemDefId, acquiredAt, acquiredLat, acquiredLng, sessionId)
acquired_spawn(spawnId, acquiredAt)
acquisition_event(id, itemInstanceId, at, lat, lng)
```

ItemDefinition の初期データはアプリ内蔵 JSON（assets/items.json）から投入。20種程度でスタート。

---

## 7. Phase 2 への布石（実装はしないが壊さない）

- ItemInstance のシリアル・取得座標・日時はそのまま NFT メタデータになる想定
- capabilities フィールド（空配列）を items.json スキーマに含めておく
- データエクスポート(JSON) 機能が Phase 2 の移行経路になる
- Wallet / Mint / ExternalAppLink 関連は一切実装しない

---

## 8. 開発ステップ案

1. リポジトリ作成＋Actions ビルド環境（既存規約流用）
2. 地図表示＋現在地（osmdroid）
3. 決定論スポーン＋マーカー表示
4. 取得判定＋Inventory/Room 保存
5. セッション記録（歩数・距離・トラック）
6. インベントリ/図鑑/履歴画面
7. Health Connect 連携＋歩数ボーナス
8. 不正対策・エクスポート・仕上げ
