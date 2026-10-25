package com.example.tuanyingshi.data.remote.hanime1

/**
 * Hanime1 高级搜索筛选项（移植自 Han1meViewer 的 assets 下 search_options 各 json，Apache-2.0）。
 *
 * 站点 `/search` 接受的查询参数：genre / sort / date / duration / tags[] / brands[]。
 * [searchKey] 为传给站点的原始关键字（null = 不传该参数，即「全部」）；
 * [label] 为界面展示用中文名。
 */

/** 单个筛选项：[searchKey] 传给站点，null 表示「全部」。 */
data class Hanime1Option(val searchKey: String?, val label: String)

/** 当前筛选状态（Hanime1 高级搜索）。 */
data class Hanime1Filter(
    val genre: String? = null,
    val sort: String? = null,
    val date: String? = null,
    val duration: String? = null,
    val tags: Set<String> = emptySet(),
    val brands: Set<String> = emptySet(),
    val broad: Boolean = false,
) {
    /** 是否有非默认筛选条件（用于 UI 徽标与「重置」按钮可用态）。 */
    val isActive: Boolean
        get() = genre != null || sort != null || date != null || duration != null ||
            tags.isNotEmpty() || brands.isNotEmpty() || broad

    companion object {
        val DEFAULT = Hanime1Filter()
    }
}

object Hanime1SearchOptions {

    /** 类型（genre）。 */
    val genres = listOf(
        Hanime1Option("全部", "全部"),
        Hanime1Option("裏番", "里番"),
        Hanime1Option("泡麵番", "泡面番"),
        Hanime1Option("Motion Anime", "Motion Anime"),
        Hanime1Option("3DCG", "3D动画"),
        Hanime1Option("2.5D", "2.5D"),
        Hanime1Option("2D動畫", "2D动画"),
        Hanime1Option("AI生成", "AI生成"),
        Hanime1Option("MMD", "MMD"),
        Hanime1Option("Cosplay", "Cosplay"),
        Hanime1Option("新番預告", "新番预告片"),
    )

    /** 排序（sort）。 */
    val sorts = listOf(
        Hanime1Option("最新上市", "最新上市"),
        Hanime1Option("最新上傳", "最新上傳"),
        Hanime1Option("本日排行", "本日排行"),
        Hanime1Option("本週排行", "本週排行"),
        Hanime1Option("本月排行", "本月排行"),
        Hanime1Option("觀看次數", "观看次数"),
        Hanime1Option("讚好比例", "点赞比例"),
        Hanime1Option("他們在看", "他们在看"),
        Hanime1Option("時長最長", "时长最长"),
    )

    /** 发布时间（date）。 */
    val dates = listOf(
        Hanime1Option(null, "全部"),
        Hanime1Option("過去 24 小時", "过去24小时"),
        Hanime1Option("過去 2 天", "过去2天"),
        Hanime1Option("過去 1 週", "过去1周"),
        Hanime1Option("過去 1 個月", "过去1个月"),
        Hanime1Option("過去 3 個月", "过去3个月"),
        Hanime1Option("過去 1 年", "过去1年"),
    )

    /** 时长（duration）。 */
    val durations = listOf(
        Hanime1Option(null, "全部"),
        Hanime1Option("1 分鐘 +", "1分钟以上"),
        Hanime1Option("5 分鐘 +", "5分钟以上"),
        Hanime1Option("10 分鐘 +", "10分钟以上"),
        Hanime1Option("20 分鐘 +", "20分钟以上"),
        Hanime1Option("30 分鐘 +", "30分钟以上"),
        Hanime1Option("60 分鐘 +", "60分钟以上"),
        Hanime1Option("0 - 10 分鐘", "0~10分钟"),
        Hanime1Option("0 - 20 分鐘", "0~20分钟"),
    )

    /** 影片标签（tags，多选）。 */
    val tags = listOf(
        Hanime1Option("無碼", "无码"),
        Hanime1Option("AI解碼", "AI解码"),
        Hanime1Option("中文字幕", "中文字幕"),
        Hanime1Option("中文配音", "中文配音"),
        Hanime1Option("同人作品", "同人作品"),
        Hanime1Option("斷面圖", "断面图"),
        Hanime1Option("ASMR", "ASMR"),
        Hanime1Option("1080p", "1080P"),
        Hanime1Option("60FPS", "60FPS"),
    )

    /** 品牌（brands，多选）。 */
    val brands = listOf(
        Hanime1Option("妄想実現めでぃあ", "妄想実現めでぃあ"),
        Hanime1Option("メリー・ジェーン", "メリー・ジェーン"),
        Hanime1Option("ピンクパイナップル", "ピンクパイナップル"),
        Hanime1Option("ばにぃうぉ～か～", "ばにぃうぉ～か～"),
        Hanime1Option("Queen Bee", "Queen Bee"),
        Hanime1Option("PoRO", "PoRO"),
        Hanime1Option("せるふぃっしゅ", "せるふぃっしゅ"),
        Hanime1Option("鈴木みら乃", "鈴木みら乃"),
        Hanime1Option("ショーテン", "ショーテン"),
        Hanime1Option("GOLD BEAR", "GOLD BEAR"),
        Hanime1Option("ZIZ", "ZIZ"),
        Hanime1Option("EDGE", "EDGE"),
        Hanime1Option("Collaboration Works", "Collaboration Works"),
        Hanime1Option("BOOTLEG", "BOOTLEG"),
        Hanime1Option("BOMB!CUTE!BOMB!", "BOMB!CUTE!BOMB!"),
        Hanime1Option("nur", "nur"),
        Hanime1Option("あんてきぬすっ", "あんてきぬすっ"),
        Hanime1Option("魔人", "魔人"),
        Hanime1Option("ルネ", "ルネ"),
        Hanime1Option("Princess Sugar", "Princess Sugar"),
        Hanime1Option("パシュミナ", "パシュミナ"),
        Hanime1Option("WHITE BEAR", "WHITE BEAR"),
        Hanime1Option("AniMan", "AniMan"),
        Hanime1Option("chippai", "chippai"),
        Hanime1Option("トップマーシャル", "トップマーシャル"),
        Hanime1Option("erozuki", "erozuki"),
        Hanime1Option("サークルトリビュート", "サークルトリビュート"),
        Hanime1Option("spermation", "spermation"),
        Hanime1Option("Milky", "Milky"),
        Hanime1Option("King Bee", "King Bee"),
        Hanime1Option("PashminaA", "PashminaA"),
        Hanime1Option("じゅうしぃまんご～", "じゅうしぃまんご～"),
        Hanime1Option("Hills", "Hills"),
        Hanime1Option("妄想専科", "妄想専科"),
        Hanime1Option("ディスカバリ���", "ディスカバリー"),
        Hanime1Option("ひまじん", "ひまじん"),
        Hanime1Option("37℃", "37℃"),
        Hanime1Option("schoolzone", "schoolzone"),
        Hanime1Option("GREEN BUNNY", "GREEN BUNNY"),
        Hanime1Option("バニラ", "バニラ"),
        Hanime1Option("L.", "L."),
        Hanime1Option("PIXY", "PIXY"),
        Hanime1Option("こっとんど～る", "こっとんど～る"),
        Hanime1Option("ANIMAC", "ANIMAC"),
        Hanime1Option("Celeb", "Celeb"),
        Hanime1Option("MOON ROCK", "MOON ROCK"),
        Hanime1Option("Dream", "Dream"),
        Hanime1Option("ミンク", "ミンク"),
        Hanime1Option("オズ・インク", "オズ・インク"),
        Hanime1Option("サン出版", "サン出版"),
        Hanime1Option("ポニーキャニオン", "ポニーキャニオン"),
        Hanime1Option("わるきゅ～れ＋＋", "わるきゅ～れ＋＋"),
        Hanime1Option("株式会社虎の穴", "株式会社虎の穴"),
        Hanime1Option("エンゼルフィッシュ", "エンゼルフィッシュ"),
        Hanime1Option("UNION-CHO", "UNION-CHO"),
        Hanime1Option("TOHO", "TOHO"),
        Hanime1Option("ミルクセーキ", "ミルクセーキ"),
        Hanime1Option("2匹目のどぜう", "2匹目のどぜう"),
        Hanime1Option("じゅうしぃまんご～", "じゅうしぃまんご～"),
        Hanime1Option("ツクルノモリ", "ツクルノモリ"),
        Hanime1Option("サークルトリビュート", "サークルトリビュート"),
        Hanime1Option("トップマーシャル", "トップマーシャル"),
        Hanime1Option("彗星社", "彗星社"),
        Hanime1Option("ナチュラルハイ", "ナチュラルハイ"),
        Hanime1Option("れもんは～と", "れもんは～と"),
    )
}
