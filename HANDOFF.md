# HANDOFF — WalkNApp

## 現在地
- v1.3: 納品規約への準拠
  - build.yml を削除。CI は release.yml（タグ起動）のみに一本化
  - deploy.sh に rm -f .github/workflows/build.yml と git rm --cached を追加（unzip -o は端末の旧ファイルを消さないため）
  - タグ発行をローカル方式に変更: git fetch --tags --force -> git tag --list 'v*' | sort -V | tail -1 から算出 -> git tag -> git push origin タグ名。GitHub API は使わない
  - 第2引数 notag で push のみ
  - ci/ と .github/workflows/release.yml は削除・追跡解除しない
- v1.2: ウォーキング速度表示
  - SessionTracker に currentSpeedMps（直近30秒の移動距離÷経過時間で平滑化）と averageSpeedMps を追加
  - 15秒以上位置更新が進まない場合は速度を 0 にリセット（decayIfIdle）
  - SpeedFormat: km/h 表記、ペース(分:秒/km)、速さラベル（停止中/ゆっくり/ふつう/早歩き/かなり速い/走行中）
  - 地図画面に「速度 x.x km/h (ラベル)」「ペース m:ss /km / 平均 x.x km/h」を記録中に表示
  - 履歴画面に所要時間・平均速度・平均ペースを追加
- v1.1: build.yml から actions/upload-artifact ステップを削除
  - Artifacts 無料枠(0.5GB)枯渇による "Artifact storage quota has been hit" 回避
  - APK は Release から配布するため Artifacts 不要。build.yml はコンパイル確認専用と割り切る
- v1.0: アイコン差し替え＋機能追加（開発ステップ6 相当まで完了）
  - アプリアイコンを歩行シルエットに差し替え（アダプティブアイコン対応、mdpi〜xxxhdpi＋round）。表示名を WalkN に変更
  - 図鑑画面を追加: 全18種の発見状況、レアリティ別集計、総歩数・総距離、用途(capabilities)とNFT化ポリシーを表示
  - 記録中の軌跡を地図上に青いポリラインで描画
  - 歩数ボーナス: セッション歩数 3000/6000/10000 で周辺セルの出現数が +1/+2/+3。ボーナス変化時にスポーン再計算
  - 地図画面のボタンは 記録開始/終了・図鑑・履歴・持ち物・現在地
- v0.9: deploy.sh を恒久仕様に差し替え
  - pull --rebase とタグ発行（GitHub API 経由）を含む1コマンド完結型
  - shebang は Termux 絶対パス
  - release buildType の signingConfig 指定を削除。配布ビルドの署名は release.yml と ci/appathy.keystore に委ねる
  - .github/workflows/release.yml と ci/ は削除しない（カタログ管理システムが API 経由でコミットするため）
- v0.8: セッション記録（開発ステップ5完了）
  - walk_session テーブル追加（Room version 3）。開始/終了ボタンで記録
  - 歩数は TYPE_STEP_COUNTER センサー（Health Connect は v0.9 で対応）
  - 距離は位置更新の差分累積。3m未満のノイズは無視、15km/h超の区間は除外して invalid_segments に計上
  - 軌跡を trackJson に保存（小数5桁に丸め）
  - 取得時に asset.acquired_steps へセッション歩数を記録（Phase2 メタデータで使用）
  - 履歴画面を追加。地図画面は 記録開始/終了・履歴・持ち物・現在地 の4ボタン構成
  - 制約: アプリを閉じると記録が止まる（フォアグラウンドサービス化は後日）
- v0.7: Phase 2 (Universal Asset Platform) 対応のデータモデル移行
  - item_instance を asset テーブルに置換。主キーを UUID 化、Room version 2
  - AssetStatus(INTERNAL/PENDING_MINT/MINTED/EXPORTED)、owner_ref/chain_ref/metadata_uri を予約列として追加
  - Collections(walkn.basic / walkn.treasure)、MintPolicy(NEVER/ON_DEMAND/AUTO)、capabilities を ItemDefinition に付与
  - AssetMetadata: Metaplex 準拠 JSON 出力。持ち物画面から共有でエクスポート可能
  - asset_event テーブルで ACQUIRE を記録（Phase 2 の AI Engine 入力）
  - 詳細は PHASE2_READINESS.md
- v0.6: 取得判定＋Room保存（開発ステップ4完了）
  - Room 2.6.1 + KSP 導入。item_instance / acquired_spawn テーブル
  - 30m以内のマーカータップで取得 → DB保存＋マーカー消滅。範囲外は距離をトースト表示
  - 取得済み spawnId は再表示されない（acquired_spawn で消し込み）
  - 持ち物画面（取得日時・座標つき一覧）を追加。地図画面に所持数を常時表示
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

## deploy.sh 恒久ルール
- pull --rebase 必須（カタログ管理システムが release.yml と ci/appathy.keystore を API 経由で直接コミットするため）
- ci/ と .github/workflows/release.yml は削除しない
- タグ発行 → Actions がビルド → Release 作成 → 自作アプリストアに更新として表示
- build.yml に actions/upload-artifact を入れない（Artifacts 無料枠枯渇でビルドが落ちるため）。APK 配布は Release 経由
