package util

object Devices {
    private const val INSTAGRAM_VERSION_OLD: String = "105.0.0.18.119"
    private const val INSTAGRAM_VERSION_NEW: String = "126.0.0.25.121"

    // Released on August 2019
    val onePlus7 = Device(
        INSTAGRAM_VERSION_NEW, 29, "10.0", "420dpi",
        "1080x2260", "OnePlus", "GM1903",
        "OnePlus7", "qcom"
    )

    // Released on February 2018
    val samsungGalaxyS9Plus = Device(
        INSTAGRAM_VERSION_OLD, 24, "7.0", "640dpi",
        "1440x2560", "samsung", "SM-G965F",
        "star2qltecs", "samsungexynos9810"
    )

    val DEFAULT_DEVICE: Device = onePlus7
}