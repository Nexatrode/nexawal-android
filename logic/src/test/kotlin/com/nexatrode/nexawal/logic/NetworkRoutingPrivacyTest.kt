package com.nexatrode.nexawal.logic

import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkRoutingPrivacyTest {
    @Test fun validHttpProxyAndI2pRpc() {
        NetworkRouting.validateI2pConfiguration(NetworkRouting.DEFAULT_I2P_RPC_ADDRESS, "127.0.0.1:4444")
        NetworkRouting.validateI2pConfiguration("http://custom.i2p:18081", "http://[::1]:4444")
    }
    @Test fun rejectsMissingAndWrongProxyProtocols() {
        for (proxy in listOf(null, "", "localhost", "127.0.0.1:0", "127.0.0.1:65536", "socks5://127.0.0.1:4447", "https://127.0.0.1:4444", "http://127.0.0.1:4444/path")) {
            assertThrows(IllegalArgumentException::class.java) {
                NetworkRouting.validateI2pConfiguration("http://test.i2p", proxy)
            }
        }
    }
    @Test fun rejectsClearnetAndP2pAddressesIncludingPendingSendEndpoints() {
        for (node in listOf("", "https://rpc.nexawal.com", "http://127.0.0.1:18092", "http://test.i2p.evil.example", "test.i2p:0", "http://test.i2p/#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { NetworkRouting.validateI2pConfiguration(node, "127.0.0.1:4444") }
        }
    }
    @Test fun hybridBroadcastRemainsI2p() {
        for (policy in listOf(NetworkRouting.Policy.I2P, NetworkRouting.Policy.HYBRID)) {
            assertTrue(NetworkRouting.broadcastNodeUrl(policy, "https://rpc.nexawal.com", NetworkRouting.DEFAULT_I2P_RPC_ADDRESS).endsWith(".b32.i2p"))
        }
    }
}
