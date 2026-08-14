# Phase 2 (Universal Asset Platform) 対応状況

参照: NFT_Universal_Asset_Platform_オントロジー設計書.md

## 設計思想の一致
「通常はアプリ内DBで管理し、価値ある資産のみNFT化する」
→ WalkNApp は全アセットを status=INTERNAL で Room に保存。NFT化は Phase 2 で選択的に行う。

## Phase 2 オントロジーとの対応表

| Platform 側 | WalkNApp Phase 1 での実装 | Phase 2 でやること |
|---|---|---|
| User | 端末内単一ユーザー（暗黙） | Platform の User にマッピング |
| Embedded Wallet | 未実装 | ウォレット生成、ownerRef に格納 |
| Internal Asset | AssetEntity (status=INTERNAL) | そのまま流用 |
| NFT Asset | AssetEntity の status/chainRef/metadataUri 列を予約済み | Solana cNFT ミント後に更新 |
| Collection | Collections（walkn.basic / walkn.treasure） | cNFT コレクションとして登録 |
| Game Adapter | ItemDefinition.capabilities（rpg.equipment 等） | Adapter が capabilities を解釈 |
| Exchange (QR交換) | asset.uuid で一意識別、transferable フラグ | QR に uuid＋署名を載せて譲渡 |
| Marketplace | capabilities に market.listable | 出品・取引 |
| Event | AssetEventEntity（ACQUIRE を記録中） | MINT/TRANSFER/LIST を追記 |
| AI Engine | AssetEventEntity が入力データになる | 提案生成 |

## Phase 1 で先回りして入れた4点

1. **UUID 主キー** — 自動採番の整数だと他アプリと衝突するため asset.uuid を UUID に
2. **AssetStatus** — INTERNAL / PENDING_MINT / MINTED / EXPORTED を最初から定義
3. **Collection** — cNFT はコレクション単位発行のため ItemDefinition に collectionId
4. **Metaplex 準拠エクスポート** — AssetMetadata.toMetaplexJson で name/symbol/attributes/properties 形式を出力

## MintPolicy（NFT化の選別基準）

| policy | 対象 | 意味 |
|---|---|---|
| NEVER | 基本素材（コモン〜アンコモン） | アプリ内DB管理のみ。NFT化しない |
| ON_DEMAND | レア・エピック | ユーザーが希望すればミント |
| AUTO | レジェンダリー | Phase 2 で取得時に自動ミント候補 |

## 予約済みで未使用のフィールド

asset.owner_ref / asset.chain_ref / asset.metadata_uri / asset.acquired_steps
→ Phase 1 では null または 0。Phase 2 でスキーマ変更なしに埋められる。

## Phase 1 で実装しないこと

ウォレット生成、鍵管理、チェーン接続、ミント、QR交換、マーケット、AI提案。
