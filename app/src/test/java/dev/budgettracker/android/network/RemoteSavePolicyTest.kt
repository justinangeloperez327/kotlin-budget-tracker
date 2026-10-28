package dev.budgettracker.android.network

import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test

/** No mutation is safe to retry before the authoritative revision is fetched. */
class RemoteSavePolicyTest {
    @Test fun expiredTokenRequiresNewLogin() {
        assertEquals(RecoveryAction.SIGN_IN_AGAIN,
            RemoteSavePolicy.actionFor(MobileApiException(401,"Session expired")))
    }

    @Test fun http400403409429And503AllRequireRefresh() {
        for(status in listOf(200,301,400,403,408,409,422,429,500,503)) {
            assertEquals("HTTP $status may have an uncertain save revision",
                RecoveryAction.REFRESH_BEFORE_EDITING,
                RemoteSavePolicy.actionFor(MobileApiException(status,"Rejected")))
        }
    }

    @Test fun invalidRevisionTransportFailureAndJsonParsingErrorRequireRefresh() {
        for(error in listOf(
            IllegalArgumentException("Unexpected revision"),
            SocketTimeoutException("No response"),
            java.io.IOException("Connection reset"),
            org.json.JSONException("Malformed success response"),
        )) {
            assertEquals(RecoveryAction.REFRESH_BEFORE_EDITING,
                RemoteSavePolicy.actionFor(error))
        }
    }
}
