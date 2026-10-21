package it.franc.casttokodi

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

// Piccolo server HTTP sul telefono che passa a Kodi file già scaricati (playlist e chiave di un video).
// La Fire TV ci mette 10-20 secondi per ogni richiesta a VixCloud e Kodi rinuncia dopo 30 (limite fisso nel suo
// demuxer FFmpeg): il telefono le scarica in un attimo e Kodi le legge in rete locale. I pezzi del video invece
// Kodi li prende direttamente dal sito, così il telefono serve solo all'avvio.
// Niente android.*: lo prova RelayTest sulla JVM.
class Relay(private val keep: Int = 20) {
    private class Entry(val body: ByteArray, val type: String)

    private val files = LinkedHashMap<String, Entry>()
    private var server: ServerSocket? = null

    // Porta su cui risponde, il server parte al primo uso e resta finché vive l'app
    @Synchronized
    fun port(): Int {
        server?.takeUnless { it.isClosed }?.let { return it.localPort }
        val socket = ServerSocket(0)
        server = socket
        thread(isDaemon = true, name = "relay") {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: IOException) { break }
                thread(isDaemon = true) { serve(client) }
            }
        }
        return socket.localPort
    }

    // path senza "/" iniziale; si tengono solo gli ultimi `keep` file
    @Synchronized
    fun put(path: String, body: ByteArray, type: String) {
        files.remove(path)
        files[path] = Entry(body, type)
        while (files.size > keep) files.remove(files.keys.first())
    }

    @Synchronized
    private fun get(path: String) = files[path]

    @Synchronized
    fun close() {
        server?.close()
        server = null
    }

    private fun serve(client: Socket) = client.use {
        try {
            it.soTimeout = 10_000
            val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val request = input.readLine() ?: return
            // Gli header di Kodi (Referer, User-Agent) qui non servono: si leggono solo per arrivare alla fine
            while (!input.readLine().isNullOrEmpty()) Unit
            val parts = request.split(" ")
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?').removePrefix("/")
            val entry = get(path)
            val out = it.getOutputStream()
            if (entry == null || method !in setOf("GET", "HEAD")) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            } else {
                out.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: ${entry.type}\r\nContent-Length: ${entry.body.size}\r\n" +
                        "Connection: close\r\n\r\n").toByteArray()
                )
                if (method == "GET") out.write(entry.body)
            }
            out.flush()
        } catch (e: IOException) {
            // Kodi ha chiuso la connessione: niente da fare
        }
    }
}
