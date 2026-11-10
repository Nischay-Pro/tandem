package app.tandemforactual.android

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSecurityPolicyTest {
  @Test
  fun cleartextIsRestrictedToExplicitLocalHosts() {
    val policy = NetworkSecurityPolicy.getInstance()

    assertFalse(policy.isCleartextTrafficPermitted("example.com"))
    assertFalse(policy.isCleartextTrafficPermitted("1.1.1.1"))
    assertTrue(policy.isCleartextTrafficPermitted("localhost"))
    assertTrue(policy.isCleartextTrafficPermitted("127.0.0.1"))
    assertTrue(policy.isCleartextTrafficPermitted("10.0.2.2"))
    assertTrue(policy.isCleartextTrafficPermitted("actual.local"))
    assertFalse(policy.isCleartextTrafficPermitted("192.168.1.20"))
  }
}
