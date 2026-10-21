package it.franc.casttokodi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.random.Random

class AdbTest {

    private val keys: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun cmd(name: String) = ByteBuffer.wrap(name.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).int

    @Test
    fun messaggio() {
        val bytes = adbEncode(AdbMessage(cmd("CNXN"), 0x01000000, 4096, "host::\u0000".toByteArray()))
        assertEquals("CNXN", String(bytes, 0, 4))
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(cmd("CNXN"), b.getInt(0))
        assertEquals(7, b.getInt(12))
        assertEquals("host::\u0000".sumOf { it.code }, b.getInt(16))
        assertEquals(cmd("CNXN").inv(), b.getInt(20))

        val back = adbRead(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(4096, back.arg1)
        assertEquals("host::\u0000", String(back.data))
    }

    @Test
    fun chiavePubblica() {
        val key = keys.public as RSAPublicKey
        val text = String(adbPublicKey(key, "Couch Cast"))
        assertTrue(text.endsWith(" Couch Cast\u0000"))
        val struct = ByteBuffer.wrap(Base64.getDecoder().decode(text.substringBefore(' '))).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(4 + 4 + 256 + 256 + 4, struct.capacity())
        assertEquals(64, struct.int)
        val n0inv = struct.int.toLong() and 0xFFFFFFFFL
        val n = ByteArray(256).also { struct.get(it) }
        val rr = ByteArray(256).also { struct.get(it) }
        assertEquals(65537, struct.int)

        val modulus = BigInteger(1, n.reversedArray())
        assertEquals(key.modulus, modulus)
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(modulus), BigInteger(1, rr.reversedArray()))
        // n0inv * n ≡ -1 (mod 2^32)
        val r32 = BigInteger.ONE.shiftLeft(32)
        assertEquals(r32.subtract(BigInteger.ONE), BigInteger.valueOf(n0inv).multiply(modulus).mod(r32))
    }

    @Test
    fun firma() {
        // adbd verifica come una firma SHA1withRSA il cui hash è il token
        val message = "token".toByteArray()
        val token = MessageDigest.getInstance("SHA-1").digest(message)
        val signature = adbSign(keys.private, token)
        val verifier = Signature.getInstance("SHA1withRSA").apply { initVerify(keys.public) }
        verifier.update(message)
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun indirizzo() {
        assertEquals("192.168.1.50", adbHost("192.168.1.50:8080"))
        assertEquals("192.168.1.50", adbHost(" 192.168.1.50 "))
    }

    // adbd finto: la prima volta non conosce la chiave e la vuole, poi accetta la firma; esegue il comando shell
    @Test
    fun shellControAdbdFinto() {
        val server = ServerSocket(0)
        var known: ByteArray? = null
        val commands = mutableListOf<String>()
        val adbd = thread {
            repeat(2) {
                server.accept().use { socket ->
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                    val output = socket.getOutputStream()
                    fun send(name: String, a0: Int, a1: Int, data: ByteArray = ByteArray(0)) =
                        output.write(adbEncode(AdbMessage(cmd(name), a0, a1, data)))

                    assertEquals(cmd("CNXN"), adbRead(input).command)
                    val token = Random.nextBytes(20)
                    send("AUTH", 1, 0, token)
                    val signed = adbRead(input)
                    assertEquals(2, signed.arg0)
                    val verifier = Signature.getInstance("NONEwithRSA").apply { initVerify(keys.public) }
                    verifier.update(byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14) + token)
                    val good = verifier.verify(signed.data)
                    if (known == null) {
                        send("AUTH", 1, 0, Random.nextBytes(20))
                        val pub = adbRead(input)
                        assertEquals(3, pub.arg0)
                        known = pub.data
                    } else {
                        assertTrue(good)
                    }
                    send("CNXN", 0x01000000, 4096, "device::\u0000".toByteArray())

                    val open = adbRead(input)
                    assertEquals(cmd("OPEN"), open.command)
                    commands += String(open.data).removeSuffix("\u0000")
                    send("OKAY", 7, open.arg0)
                    send("WRTE", 7, open.arg0, "ok\n".toByteArray())
                    assertEquals(cmd("OKAY"), adbRead(input).command)
                    send("CLSE", 7, open.arg0)
                    assertEquals(cmd("CLSE"), adbRead(input).command)
                }
            }
        }

        var asked = 0
        val first = adbShell("127.0.0.1", keys, "Couch Cast", "echo ok", onConfirm = { asked++ }, port = server.localPort)
        val second = adbShell("127.0.0.1", keys, "Couch Cast", WAKE_AND_OPEN_KODI, onConfirm = { asked++ }, port = server.localPort)
        adbd.join(5000)
        server.close()

        assertEquals("ok\n", first)
        assertEquals("ok\n", second)
        // solo la prima volta si chiede di accettare sulla TV
        assertEquals(1, asked)
        assertArrayEquals(adbPublicKey(keys.public as RSAPublicKey, "Couch Cast"), known)
        assertEquals(listOf("shell:echo ok", "shell:$WAKE_AND_OPEN_KODI"), commands)
    }
}
