package dev.budgettracker.android.network

/**
 * A remote write that failed to produce a confirmed revision cannot be retried
 * automatically: HTTP errors, invalid JSON, timeouts and connection resets may
 * happen AFTER the server has committed the workspace.
 *
 * 401 is handled separately by clearing the invalid mobile session.
 */
object RemoteSavePolicy {
    fun actionFor(error: Throwable): RecoveryAction =
        if(error is MobileApiException && error.status==401)
            RecoveryAction.SIGN_IN_AGAIN
        else
            RecoveryAction.REFRESH_BEFORE_EDITING
}

enum class RecoveryAction {
    SIGN_IN_AGAIN,
    REFRESH_BEFORE_EDITING
}
