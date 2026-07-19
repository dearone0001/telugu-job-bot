package com.example.telugujobalerts

object AdConfig {
    // ⚠️ REAL IDs from your screenshot
    private const val REAL_BANNER_ID = "ca-app-pub-1300897875271635/326824991"
    private const val REAL_INTERSTITIAL_ID = "ca-app-pub-1300897875271635/8057617240"

    // 🧪 GOOGLE TEST IDs (Use these to verify ads work)
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    // Set this to 'false' when you want to show REAL ads
    const val USE_TEST_ADS = true

    val BANNER_AD_UNIT_ID = if (USE_TEST_ADS) TEST_BANNER_ID else REAL_BANNER_ID
    val INTERSTITIAL_AD_UNIT_ID = if (USE_TEST_ADS) TEST_INTERSTITIAL_ID else REAL_INTERSTITIAL_ID
}
