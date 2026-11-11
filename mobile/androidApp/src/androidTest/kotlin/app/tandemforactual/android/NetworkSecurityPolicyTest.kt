package app.tandemforactual.android

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSecurityPolicyTest {
  @Test
  fun cleartextSupportsPrivateLanHosts() {
    val policy = NetworkSecurityPolicy.getInstance()

    // The app client separately rejects public HTTP; Android's config cannot match private CIDRs.
    assertTrue(policy.isCleartextTrafficPermitted("example.com"))
    assertTrue(policy.isCleartextTrafficPermitted("1.1.1.1"))
    assertTrue(policy.isCleartextTrafficPermitted("192.168.1.20"))
    assertTrue(policy.isCleartextTrafficPermitted("10.0.0.12"))
    assertTrue(policy.isCleartextTrafficPermitted("172.31.0.8"))
    assertTrue(policy.isCleartextTrafficPermitted("fd12::1"))
    assertTrue(policy.isCleartextTrafficPermitted("localhost"))
    assertTrue(policy.isCleartextTrafficPermitted("127.0.0.1"))
    assertTrue(policy.isCleartextTrafficPermitted("10.0.2.2"))
    assertTrue(policy.isCleartextTrafficPermitted("actual.local"))
  }

  @Test
  fun cleartextHttpConnectionCanReachALocalServer() {
    ServerSocket(0).use { server ->
      val responseThread =
        Thread {
          server.accept().use { socket ->
            socket.soTimeout = 5_000
            val request = socket.getInputStream().bufferedReader()
            while (request.readLine()?.isNotEmpty() == true) {
              // Read the request headers before replying.
            }
            socket.getOutputStream().apply {
              write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".encodeToByteArray())
              flush()
            }
          }
        }
      responseThread.start()

      val connection = URL("http://127.0.0.1:${server.localPort}/health").openConnection() as HttpURLConnection
      try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        assertTrue(connection.inputStream.bufferedReader().use { it.readText() } == "ok")
      } finally {
        connection.disconnect()
        responseThread.join(5_000)
      }
      assertTrue(!responseThread.isAlive)
    }
  }
}
