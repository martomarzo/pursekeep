package app.pursekeep.capture

import kotlinx.serialization.Serializable

/** Wire payload for POST /api/wallet/capture — see docs/wallet-capture-contract.md (v1). */
@Serializable
data class AndroidCapture(
    val kind: String = "android_notification",
    val app: String,
    val title: String,
    val text: String,
    val postedAt: String,
)
