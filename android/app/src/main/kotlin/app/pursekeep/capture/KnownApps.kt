package app.pursekeep.capture

data class KnownApp(val packageName: String, val label: String)

object KnownApps {
    const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
    val all: List<KnownApp> = listOf(
        KnownApp(GOOGLE_WALLET, "Google Wallet"),
        KnownApp("com.revolut.revolut", "Revolut"),
        KnownApp("com.transferwise.android", "Wise"),
    )
    val defaultEnabled: Set<String> = setOf(GOOGLE_WALLET)
}
