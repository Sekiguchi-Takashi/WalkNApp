# WalkNApp 画像素材パック v1

透過PNG（マゼンタ背景を除去済み・縁のマゼンタかぶりも補正済み）。

## 収録内容（29枚）

### 靴（512x512）
| ファイル | 用途 |
|---|---|
| shoe_stroller.png | ストローラー 2.0〜4.5km/h 新品 |
| shoe_stroller_worn.png | ストローラー 消耗 |
| shoe_walker.png | ウォーカー 4.0〜7.0km/h 新品（意匠を刷新して再生成） |
| shoe_walker_worn.png | ウォーカー 消耗 |
| shoe_speedwalker.png | スピードウォーカー 6.0〜9.5km/h 新品（意匠を刷新して再生成） |
| shoe_speedwalker_worn.png | スピードウォーカー 消耗 |
| shoe_broken.png | 耐久0 の破損表示 |

### 報酬アイテム（512x512）
| ファイル | ランク |
|---|---|
| item_low.png | 低ランク 修理1pt |
| item_mid.png | 中ランク 修理2〜5pt |
| item_high.png | 高ランク 修理5〜10pt |

### UIアイコン（192x192）
icon_repair / icon_durability / icon_streak / icon_speed_ok /
icon_speed_slow / icon_speed_fast / icon_indoor / icon_gps_weak / icon_wallet

### ウェア立ち絵（高さ1000px）
| ファイル | ウェア |
|---|---|
| wear_light_boy / wear_light_girl | LIGHT 5分ごと・低ランク |
| wear_standard_boy / wear_standard_girl | STANDARD 15分・中ランク |
| wear_serious_boy / wear_serious_girl | SERIOUS 30分・高ランク |

### 演出（高さ1000px）
state_empty_boy / state_empty_girl … 持ち物ゼロ
state_celebrate_boy / state_celebrate_girl … 60分達成・連続更新

## 加工履歴（商標対策）
- shoe_speedwalker: 「RACER」「SPEED PRO」「CARBON X」の文字と稲妻ロゴを除去
- wear_standard_girl: 胸ロゴを除去
- wear_serious_girl: 胸ロゴ・腿ロゴを除去
- wear_serious_boy: 靴のタンのロゴ2箇所を除去

## 不足している素材
なし。Phase 1 (v2.0) に必要な素材は揃っています。

## 既知の課題
- item_low にのみ英字「REPAIR KIT」が入る。小サイズでは判読不能なので実害は薄い
- item_mid は発光を保つためソフトキー（背景色との距離からアルファを生成）で抜いている。
  発光部にわずかに背景色が残るが暗背景では気にならない
- item_high の星の演出は生成画像には無く、後からプログラムで追加している
  （中ランクだけ発光してランク差が逆転していたため）

## 再生成時の追加ネガティブプロンプト
brand logo, brand name, lettering, printed text, side stripes, three stripes,
swoosh, tiger stripes, trademark, product branding

ウォーカーの側面は「a single wide diagonal color band on the side, no stripes」と指定すると
ブランド想起を避けつつ靴らしさを保てます。

## 消耗版の構図について
新品版と消耗版でズーム率が異なると、装備画面で耐久が下がったときに靴の大きさが変わって見えます。
再生成時は新品版と同じ画角を指定するか、新品画像にプログラムで汚し処理をかける方式を推奨します。

## Android への配置
app/src/main/res/drawable-nodpi/ に配置。ベクター化はしないでください（絵柄が崩れます）。
