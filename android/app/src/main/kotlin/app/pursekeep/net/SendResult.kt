package app.pursekeep.net

sealed class SendResult {
    /** 2xx: serverStatus is booked | needs_account | unparsed | duplicate | ok */
    data class Sent(val serverStatus: String) : SendResult()
    /** 401: token revoked or unknown — stop and mark unpaired */
    data object Unauthorized : SendResult()
    /** 4xx other than 401: permanent, never retried */
    data class Rejected(val error: String) : SendResult()
    /** 5xx / network: keep pending, retry with backoff */
    data class Retry(val reason: String) : SendResult()
}
