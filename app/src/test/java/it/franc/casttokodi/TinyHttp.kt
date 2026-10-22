package it.franc.casttokodi

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

// Server HTTP minimo per i finti dei test (FakeKodi, FakeSite), come Relay: i test Android sulla JVM vedono solo
// le classi di android.jar, e com.sun.net.httpserver non c'è.
class TinyHttp(private val handle: (Request) -> Response) : AutoCloseable {
    class Request(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray) {
        fun header(name: String) = headers[name.lowercase()]
    }

    class Response(val code: Int, val body: ByteArray = ByteArray(0), val type: String = "application/octet-stream")

    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    val port = socket.localPort

    init {
        thread(isDaemon = true, name = "tiny-http") {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: IOException) { break }
                thread(isDaemon = true) { serve(client) }
            }
        }
    }

    override fun close() = socket.close()

    private fun serve(client: Socket) = client.use {
        try {
            val input = it.getInputStream()
            val parts = readLine(input).split(" ")
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
            }
            val body = ByteArray(headers["content-length"]?.toIntOrNull() ?: 0)
            var read = 0
            while (read < body.size) {
                val n = input.read(body, read, body.size - read)
                if (n < 0) break
                read += n
            }
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty()
            val response = handle(Request(method, path.substringBefore('?'), headers, body))
            val out = it.getOutputStream()
            out.write(
                ("HTTP/1.1 ${response.code} ${if (response.code == 200) "OK" else "Error"}\r\n" +
                    "Content-Type: ${response.type}\r\nContent-Length: ${response.body.size}\r\nConnection: close\r\n\r\n").toByteArray()
            )
            if (method != "HEAD") out.write(response.body)
            out.flush()
        } catch (e: IOException) {
            // Il client ha chiuso: niente da fare
        }
    }

    private fun readLine(input: InputStream): String {
        val line = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) line.write(b)
        }
        return String(line.toByteArray(), Charsets.ISO_8859_1)
    }
}
