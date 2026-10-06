package com.connor.nearestplane

import androidx.work.NetworkType
import com.connor.nearestplane.wx.WxRefreshWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshRequestTest {
    // The refresh-now jobs are queued from the background too (each widget's
    // refresh button and its updatePeriodMillis backup). Without a declared
    // network, Android can run them while this app's network is blocked, and
    // the tile says offline even though the same refresh from the app works.
    @Test fun `the planes refresh-now job declares that it needs the network`() {
        assertEquals(
            NetworkType.CONNECTED,
            RefreshWorker.oneShot().workSpec.constraints.requiredNetworkType
        )
    }

    @Test fun `the weather refresh-now job declares that it needs the network`() {
        assertEquals(
            NetworkType.CONNECTED,
            WxRefreshWorker.oneShot().workSpec.constraints.requiredNetworkType
        )
    }
}
