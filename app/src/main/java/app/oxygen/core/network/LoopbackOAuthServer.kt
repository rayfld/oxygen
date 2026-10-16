package app.oxygen.core.network
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
class LoopbackOAuthServer {
    data class Outcome(val code: String?, val state: String?, val error: String?)
    private var socket: ServerSocket? = null
    fun start(): Int {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        socket = server
        return server.localPort
    }
    fun await(timeoutMs: Int = 5 * 60 * 1000): Outcome {
        val server = socket ?: return Outcome(null, null, "Sign-in was not started")
        return try {
            server.soTimeout = timeoutMs
            server.accept().use { client -> respond(client) }
        } catch (e: Exception) {
            Outcome(null, null, "Sign-in timed out")
        } finally {
            close()
        }
    }
    fun close() {
        runCatching { socket?.close() }
        socket = null
    }
    private fun respond(client: Socket): Outcome {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine().orEmpty()
        val target = requestLine.split(' ').getOrNull(1).orEmpty()
        val query = target.substringAfter('?', "")
        val params = query.split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                part.substring(0, separator) to URLDecoder.decode(part.substring(separator + 1), "UTF-8")
            }
        }.toMap()
        writeHtml(
            client.getOutputStream(),
            """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Oxygen</title></head>
            <body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;
            background:#09090b;color:#fafafa;font-family:system-ui,-apple-system,sans-serif">
            <div style="text-align:center">
            <p style="font-size:17px;margin:0 0 6px">Signed in</p>
            <p style="font-size:14px;color:#a1a1aa;margin:0">You can close this tab and return to Oxygen.</p>
            </div></body></html>
            """.trimIndent(),
        )
        return Outcome(params["code"], params["state"], params["error"])
    }
    private fun writeHtml(out: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
