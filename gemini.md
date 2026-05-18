# 開発の教訓とベストプラクティス

## 課題1: Sinytra Connector 経由時の Mixin 適用エラーによる起動クラッシュ
Fabric 向けに開発された Mod を、Sinytra Connector を介して NeoForge 環境 (Mojangマッピング) で動かす際、特定の Mixin が適用されず起動時にクラッシュする。

### 具体的なエラー例
```
Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError: Constant modifier method changeTimeUntilRegen(...) in seven-elements.mixins.json:LivingEntityMixin from mod seven_elements failed injection check, (0/1) succeeded. Scanned 0 target(s).
```

### 原因の分析
`@ModifyConstant` を用いてメソッド内の定数（例: `intValue = 20`）を書き換えようとしていたが、NeoForge 環境 (または NeoForge 向けに Sinytra Connector がパッチしたバイトコード) では、対象メソッド内の定数代入処理がメソッド呼び出しや異なる設計にリファクタリングされていたため、バイトコード上に定数ロード命令 (LDC) が存在しなかった。このため、インジェクションターゲットが見つからずクラッシュが発生した。

### 汎用的な教訓と解決策
1. **`@ModifyConstant` の使用を避ける**
   環境依存や Minecraft のバージョンアップによるクラス内部のリファクタリングにより、定数がハードコードからメソッド経由での取得に変更されるケースが多い。このため、定数を直接ターゲットにする `@ModifyConstant` は壊れやすい。

2. **`@Inject` + フィールド上書きによる代替**
   定数の書き換えを行う代わりに、対象メソッドの処理が完了した直後（`@At("RETURN")`）に割り込み、処理が正常に行われたこと（戻り値の確認など）を条件に、該当フィールド（例: `this.timeUntilRegen`）を直接変更するアプローチに切り替える。
   
   * **メリット**: 定数のロード命令の有無や、リファクタリングされた内部ロジックに依存しないため、Fabric と NeoForge の両環境で動作する堅牢な互換性を確保できる。

3. **Mixin の親クラスからの継承フィールドの取り扱い**
   Mixin 対象クラス（例: `LivingEntity`）が親クラス（例: `Entity`）から継承しているフィールドに対して処理を行う場合、Yarnマッピング上ですでに親クラスに定義されているため、`@Shadow` アノテーションによる再定義は不要であり、直接 `this.fieldName` でアクセス可能である。

---

## 課題2: 互換レイヤー対応時のダミークラス定義による画面フリーズ
コンパイル環境下でマッピング違い（Yarn vs Mojang）による参照エラー（例: 匿名クラス `$4` や存在しないクラス）を解決しようとして、Mod ソースコード内に Minecraft コアパッケージ（`net.minecraft` など）と同名・同完全修飾名 (FQN) のダミークラスを作成した際、ゲーム起動時にエラーを伴わずに画面が完全にハングアップ（フリーズ）する。

### 原因の分析
JVM や Minecraft のクラスローダーの管轄下において、同一 FQN を持つクラスが Mod の JAR 内と本体のクラスパスの双方に存在すると、クラスローダーが不整合や循環参照に陥り、デッドロックを引き起こす。
また、Sinytra Connector のような互換リマッパーがバイトコードをパッチする際、すでにターゲットと同じダミークラスが Mod 側からロードされていると、リマッパー of 静的解析およびバイトコード注入ロジックが無限ループに陥り、クラッシュログすら出力できずにフリーズする。

### 汎用的な教訓と解決策
1. **コアパッケージ（`net.minecraft` 配下）にダミークラスを絶対に定義しない**
   * **原則**: コンパイルエラーを回避するための一時的なダミークラスであっても、物理的に JAR 内に出力されてしまうため、実行時のクラス衝突（デッドロックや検証エラー）の致命的な引き金になる。絶対に避けるべき「アンチパターン」である。
   
2. **互換リマッパーの自動変換能力を活用する**
   * **原則**: Sinytra Connector 等の互換レイヤーは、Fabric (Yarn) 向けに書かれた Mixin を自動的に Mojang マッピングへとリマップして適用する。個別マッピング向けに Mixin クラスを複製・新規作成する必要はない。

3. **コンパイラ都合のバリデーションは build.gradle 側で制御する**
   * **原則**: Mixin アノテーションプロセッサのターゲットクラス存在チェックなど、コンパイル時のみに発生する制約は、ダミークラスによる解決ではなく、アノテーションプロセッサの引数（例: `-Amixin.target.validation=false` など）で無効化するのが正しく安全な設計である。

4. **脆弱なターゲット（匿名クラス等）への Mixin は `require = 0` のフォールバックを活用する**
   * **原則**: コンパイラの違いによりインデックス（例: `$4`）がブレやすいターゲットへの Mixin は、`require = 0` を指定してソフトアタッチにする。これにより、リマップが部分的に失敗しても、起動クラッシュを起こさずに安全に動作し続ける堅牢なコードを構築できる。

---

## 課題3: カスタムレジストリ生成と静的フィールド初期化の循環による起動時デッドロック
Sinytra Connector などの動的 Mod 変換環境や FML (Forge Mod Loader) の起動プロセスにおいて、Fabric Mod で作成したカスタムレジストリにエントリを登録する際、ゲームが何のログも残さずに特定のレジストリ登録フェーズで無限にハングアップ（フリーズ）する。

### 原因の分析
1. **クラス初期化ロック**: `Registry` の定義オブジェクトや、パケットのシリアライズ処理などが、レジストリ構築（`buildAndRegister()`）の前にクラスロードされると、JVM のクラス初期化ロック（静的イニシャライザスレッド）が発生する。
2. **Connector 同期デッドロック**: `FabricRegistryBuilder.buildAndRegister()` が Mod 初期化フェーズ (`onInitialize()`) で同期的に実行された際、Sinytra Connector は Fabric の動的レジストリ要求をインターセプトして NeoForge 側の同期スレッドと通信します。しかし、FML は全 Mod の初期化フェーズが完了するまでレジストリ確定を待機するため、ここでスレッド間の循環待機（デッドロック）が発生し、ハングアップを引き起こします。

### 汎用的な教訓と解決策
1. **静的フィールド初期化子からのレジストリ構築の完全な排除 (極めて重要)**
   * **原則**: カスタムレジストリのフィールド（例: `public static Registry<T> REGISTRY`）は `final` にせず、初期値を `null` とし、明示的かつクラス初期化が完了した安全な状態で呼び出される `load()` メソッド内で初期化を行う。
   * **メリット**: クラスロード時には単に `null` が代入されるだけであり、クラス初期化ロックを即座に解放できるため、後続 of `buildAndRegister()` 同期処理でスレッドデッドロックが発生するのを完全に回避できる。
   
   ```java
   public final class SevenElementsRegistries {
       public static Registry<ElementalReaction> ELEMENTAL_REACTION = null;

       public static void load() {
           if (ELEMENTAL_REACTION == null) {
               ELEMENTAL_REACTION = createRegistry(...);
           }
       }
   }
   ```

2. **安全かつ標準的な `FabricRegistryBuilder` の使用 (最新の決定的な対策)**
   * **原則**: Minecraft 1.21.1 / NeoForge 環境下において、バニラのルートレジストリである `Registries.REGISTRIES` に対して `Registry.register(Registries.REGISTRIES, ...)` で直接 `SimpleRegistry` を手動登録する手法は、凍結済みのレジストリ書き換えや並列ロードスレッドとの競合により、JVM全体を巻き込む致命的なデッドロック (画面フリーズ) を確実に引き起こす。
   * **解決策**: 静的フィールドやシリアライザの循環参照が完全に解消された状態であれば、Fabric 標準の `FabricRegistryBuilder.createSimple(...).buildAndRegister()` を使用してカスタムレジストリを登録する。
   * **メリット**: Sinytra Connector の動的ブリッジが安全に登録要求をインターセプトし、NeoForge 側の正しいライフサイクルイベント (`RegisterEvent`) で競合なくレジストリを処理できるため、起動時フリーズを根底から完全に排除できる。

   ```java
   public static void load() {
       if (ELEMENTAL_REACTION == null) {
           ELEMENTAL_REACTION = FabricRegistryBuilder.createSimple(SevenElementsRegistryKeys.ELEMENTAL_REACTION)
               .attribute(RegistryAttribute.SYNCED)
               .buildAndRegister();
       }
   }
   ```

3. **コンストラクタ内でのレジストリ参照や副作用の完全な排除**
   * **原則**: 登録するオブジェクト（例: `ElementalReaction` などのレジストリエントリ）のコンストラクタは純粋なデータ保持のみに徹し、レジストリアクセスやエントリ作成（`createEntry` など）を絶対に行わない。
   
4. **`createEntry` の事前呼び出しは不要**
   * **原則**: Minecraft のレジストリシステムでは、`Registry.register(...)` を呼び出した時点で自動的かつ安全に `createEntry` が内部で処理されて登録が完了する。事前に手動で `createEntry` を呼ぶ必要は一切なく、むしろ循環デッドロックを引き起こすため完全に「アンチパターン」である。

5. **ベストプラクティス (パケットのシリアライズ)**
   * **原則**: カスタムレジストリの値やエントリをパケット（Payload）で送受信する際、静的初期化子で `SevenElementsRegistries.ELEMENTAL_REACTION.getCodec()` のようにレジストリインスタンスを直接参照して `getCodec()` を呼び出してはならない。代わりに `PacketCodecs.registryValue(RegistryKey)` を用いて `RegistryKey` 定数を渡すことで、レジストリの評価を実行時まで遅延させ、クラスロード時の循環参照とデッドロックを完全に回避する。

---

## 課題4: カスタムレジストリにおける `Missing intrusive holder` アサーションエラーによる起動クラッシュ
ゲーム起動時に `java.lang.AssertionError: Missing intrusive holder for ResourceKey[...]` が発生し、Mod のロード中にクラッシュする。

### 原因の分析
Minecraft（特に1.20や1.21以降）では、ブロックやアイテムなどバニラのオブジェクトに対する下位互換性（Holder への自動移行）を目的として、レジストリ内に登録されるオブジェクト自身が Holder への参照を保持させる「イントルージブホルダー（Intrusive Holder）」の仕組みが利用されている。
カスタムレジストリ（`SimpleRegistry` など）を自前で作成する際、コンストラクタの `useIntrusiveHolders` 引数を誤って `true` に設定してしまうと、そのレジストリに登録されるカスタムオブジェクト（例: `ElementalReaction` など）に対してもイントルージブホルダーのフィールド設定が強制される。しかし、カスタムオブジェクトがそのフィールドを持っていない（あるいは定義していない）ため、レジストリ登録時にアサーションエラーとなって起動クラッシュを引き起こす。

### 汎用的な教訓と解決策
1. **新規カスタムレジストリではイントルージブホルダーを無効化する (`false` に設定する)**
   * **原則**: Mod が新しく追加する独自のカスタムレジストリ（バニラのブロックやアイテムではないもの）では、イントルージブホルダーの仕組みを有効にする必要はない。`SimpleRegistry` を構築する際は `useIntrusiveHolders` 引数に必ず `false` を渡す。
2. **`useIntrusiveHolders` の本来の目的を理解する**
   * **原則**: イントルージブホルダーは、Minecraftのバニラシステムにおいて古い参照と新規の `Holder` システムを接続するための一時的な移行措置である。Modの新規クラスでは `false` にするのが基本であり、不必要に `true` に設定すると意図しない制約（フィールドの要求など）が発生し、保守性を損なうため避けるべきである。

---

## 課題5: 互換レイヤー環境における `@Local` 依存による起動時 Mixin 適用クラッシュ
Fabric 向けに開発された Mod を、Sinytra Connector などの互換レイヤーを介して NeoForge 環境で動かす際、`@Local` を用いてターゲットメソッド内のローカル変数を抽出している Mixin が適用されず、起動時にクラッシュする。

### 原因の分析
1. **メソッド内部のローカル変数構造の違い**:
   Yarnマッピング（Fabric）とMojangマッピング（NeoForge）とでは、一見同じに見える処理であっても、メソッドの引数構成や、コンパイラが生成するローカル変数テーブル（LVT）が大きく異なる場合がある。
   例えば、Yarnマッピングの `InGameHud.renderArmor(...)` が Mojangマッピングの `Gui.renderArmorLevel(GuiGraphics)` にリマップされた際、元の引数（`PlayerEntity, int y, int x` など）がターゲットメソッドの引数から消滅し、内部ローカル変数としてしか存在しなくなる。
2. **`@Local(ordinal)` の不整合**:
   このようなメソッド構成の乖離がある中で、リマッパーが自動的にインジェクションターゲットを置き換えた際、元の Mixin が `@Local(ordinal = 4) int y` などの記述でローカル変数を指定していると、新しいターゲットメソッド（例: `renderArmorLevel`）内のローカル変数インデックス（ordinal）と一致しなくなる。結果として、クラスロード時に `SugarApplicationException: Unable to find matching local!` が発生して起動クラッシュを引き起こす。

### 汎用的な教訓と解決策
1. **`@Local` アノテーションへの依存を完全に排除する (極めて重要)**
   * **原則**: 異なるマッピングや実行環境（Fabric と Sinytra Connector 経由の NeoForge）の間で、メソッド内部のローカル変数テーブル（LVT）の整合性を保証することは不可能に近い。そのため、`@Local`（特に `ordinal` 指定）を用いてローカル変数を取り出す設計は極めて脆く、クラッシュの温床となる。
   
2. **`@Inject(at = @At("TAIL"))` などの安全なインジェクションポイントと自己計算の活用**
   * **解決策**: メソッドの末尾（`TAIL`）や安全なポイントにインジェクションし、必要な引数や状態（プレイヤーのインスタンス、画面解像度など）をバニラの提供する安全なAPI経由で取得するか、独自に計算する設計にする。
   * **メリット**: ターゲットメソッドの引数リストや LVT の変更に一切影響されず、コンパイラやマッピング環境の違いに対して完全に堅牢（Robust）な Mixin を構築できる。
   
   * **具体例 (HUD座標の自己計算)**:
     ```java
     // 脆弱な @Local(ordinal) に頼る代わりに、TAILで割り込み、画面サイズとプレイヤー状態から座標を自己計算する
     @Inject(method = "renderStatusBars", at = @At("TAIL"))
     private void renderAppliedElements(DrawContext context, CallbackInfo ci) {
         PlayerEntity player = this.client.getCameraEntity() instanceof PlayerEntity ? (PlayerEntity) this.client.getCameraEntity() : null;
         if (player == null) return;
         
         int width = this.client.getWindow().getScaledWidth();
         int height = this.client.getWindow().getScaledHeight();
         
         int x = width / 2 - 91;
         int y = height - 39;
         
         // @Shadow で定義したバニラの getHeartRows メソッドを利用して、ヘルスバーの行数を正確に自己計算する
         int maxHealth = MathHelper.ceil(player.getMaxHealth());
         int absorption = MathHelper.ceil(player.getAbsorptionAmount());
         int lines = this.getHeartRows(maxHealth + absorption);
         
         y -= lines * 10;
         if (player.getArmor() > 0) y -= 10;
         
         // 描画処理...
     }
     ```

3. **付随する潜在バグの解消**
   * **原則**: 特定の条件付きメソッド（例: アーマー値が 0 より大きい時のみ呼ばれる `renderArmor`）にインジェクションするのではなく、上位のライフサイクルメソッド（例: `renderStatusBars` の `TAIL`）にインジェクションして内部で条件判定を行うことで、「アーマー値が 0 の時に描画処理が走らない」といった設計上の潜在的なバグも未然に防ぐことができる。

