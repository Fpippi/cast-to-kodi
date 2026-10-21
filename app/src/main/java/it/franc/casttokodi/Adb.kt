package it.franc.casttokodi

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.crypto.Cipher

// Client ADB minimo per accendere la Fire TV e aprire Kodi (#31): TCP sulla porta 5555, accesso con una chiave RSA
// del telefono e un comando shell. Niente API Android né librerie (dadb & co.): i test lo provano sulla JVM
// contro un adbd finto. Protocollo: platform/packages/modules/adb, file protocol.txt.

const val ADB_PORT = 5555

// Sveglia la Fire TV (con HDMI-CEC accende anche la TV e la porta sul suo ingresso) e mette Kodi in primo piano
const val WAKE_AND_OPEN_KODI = "input keyevent KEYCODE_WAKEUP; monkey -p org.xbmc.kodi -c android.intent.category.LAUNCHER 1"

private const val A_CNXN = 0x4e584e43
private const val A_AUTH = 0x48545541
private const val A_OPEN = 0x4e45504f
private const val A_OKAY = 0x59414b4f
private const val A_CLSE = 0x45534c43
private const val A_WRTE = 0x45545257
private const val A_STLS = 0x534c5453

// Con la versione 0x01000000 adbd controlla ancora la somma dei byte: la mandiamo sempre
private const val ADB_VERSION = 0x01000000
private const val MAX_PAYLOAD = 4096

private const val AUTH_TOKEN = 1
private const val AUTH_SIGNATURE = 2
private const val AUTH_RSAPUBLICKEY = 3

private const val LOCAL_ID = 1

// DigestInfo di SHA-1: adbd tratta il token come un hash SHA-1 già fatto e verifica con RSA_verify(NID_sha1)
private val SHA1_PREFIX = byteArrayOf(
    0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
)

class AdbError(message: String) : Exception(message)

class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray = ByteArray(0))

// Intestazione di 24 byte little-endian (comando, due argomenti, lunghezza, somma dei byte, comando negato) e dati
fun adbEncode(message: AdbMessage): ByteArray {
    val data = message.data
    return ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(message.command)
        .putInt(message.arg0)
        .putInt(message.arg1)
        .putInt(data.size)
        .putInt(data.sumOf { it.toInt() and 0xFF })
        .putInt(message.command.inv())
        .put(data)
        .array()
}

fun adbRead(input: DataInputStream): AdbMessage {
    val header = ByteArray(24).also { input.readFully(it) }
    val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    val command = b.int
    val arg0 = b.int
    val arg1 = b.int
    val length = b.int
    b.int // somma dei byte: TCP è già affidabile
    if (b.int != command.inv()) throw IOException("Messaggio ADB non valido")
    if (length !in 0..1024 * 1024) throw IOException("Messaggio ADB troppo lungo")
    return AdbMessage(command, arg0, arg1, ByteArray(length).also { input.readFully(it) })
}

// La chiave pubblica nel formato di adbd (struct RSAPublicKey di libcrypto_utils, in Base64) seguita da " nome\0".
// È quella che la Fire TV salva quando si sceglie "Consenti sempre da questo computer".
fun adbPublicKey(key: RSAPublicKey, name: String): ByteArray {
    val n = key.modulus
    val words = (n.bitLength() + 31) / 32
    val r32 = BigInteger.ONE.shiftLeft(32)
    val n0inv = r32.subtract(n.mod(r32).modInverse(r32)) // -1/n mod 2^32, per la moltiplicazione di Montgomery
    val rr = BigInteger.ONE.shiftLeft(words * 32 * 2).mod(n)
    val struct = ByteBuffer.allocate(4 + 4 + words * 8 + 4).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(words)
        .putInt(n0inv.toInt())
        .put(littleEndian(n, words * 4))
        .put(littleEndian(rr, words * 4))
        .putInt(key.publicExponent.toInt())
        .array()
    return (Base64.getEncoder().encodeToString(struct) + " " + name + "\u0000").toByteArray()
}

private fun littleEndian(value: BigInteger, size: Int): ByteArray {
    val big = value.toByteArray()
    return ByteArray(size) { i -> big.getOrElse(big.size - 1 - i) { 0 } }
}

// Firma PKCS#1 v1.5 del token così com'è (senza rifare l'hash): RSA con la chiave privata su DigestInfo + token
fun adbSign(key: PrivateKey, token: ByteArray): ByteArray =
    Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(SHA1_PREFIX + token)

// L'IP della Fire TV dall'indirizzo di Kodi "IP:porta": ADB ascolta sullo stesso IP, porta 5555
fun adbHost(kodiHost: String): String = kodiHost.trim().substringBefore(':')

// Esegue command sulla Fire TV e ne restituisce l'output, da un thread di background. La prima volta la Fire TV
// non conosce la chiave e chiede sullo schermo se accettarla: allora parte onConfirm (sul thread di rete) e si
// aspetta fino a confirmTimeout millisecondi. I problemi di rete restano IOException.
fun adbShell(
    host: String,
    keys: KeyPair,
    keyName: String,
    command: String,
    onConfirm: () -> Unit,
    port: Int = ADB_PORT,
    confirmTimeout: Int = 60_000,
): String = Socket().use { socket ->
    socket.connect(InetSocketAddress(host, port), 3000)
    socket.soTimeout = 10_000
    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    val output = socket.getOutputStream()
    fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        output.write(adbEncode(AdbMessage(command, arg0, arg1, data)))
        output.flush()
    }

    send(A_CNXN, ADB_VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray())
    var signed = false
    while (true) {
        val m = adbRead(input)
        when (m.command) {
            A_CNXN -> break
            A_AUTH -> if (m.arg0 != AUTH_TOKEN) {
                throw AdbError("Risposta ADB inattesa")
            } else if (!signed) {
                signed = true
                send(A_AUTH, AUTH_SIGNATURE, 0, adbSign(keys.private, m.data))
            } else {
                // Firma rifiutata: chiave nuova, la Fire TV la mostra sullo schermo e aspetta un sì
                send(A_AUTH, AUTH_RSAPUBLICKEY, 0, adbPublicKey(keys.public as RSAPublicKey, keyName))
                onConfirm()
                socket.soTimeout = confirmTimeout
            }
            // Solo il "Debug wireless" di Android 11+ vuole TLS; il Debug ADB della Fire TV no
            A_STLS -> throw AdbError("La Fire TV chiede ADB con TLS, che l'app non supporta")
            else -> throw AdbError("Risposta ADB inattesa")
        }
    }
    socket.soTimeout = 10_000

    send(A_OPEN, LOCAL_ID, 0, "shell:$command\u0000".toByteArray())
    val out = ByteArrayOutputStream()
    while (true) {
        val m = adbRead(input)
        when (m.command) {
            A_OKAY -> {}
            A_WRTE -> {
                out.write(m.data)
                send(A_OKAY, LOCAL_ID, m.arg0)
            }
            A_CLSE -> {
                send(A_CLSE, LOCAL_ID, m.arg0)
                break
            }
            else -> throw AdbError("Risposta ADB inattesa")
        }
    }
    out.toString("UTF-8")
}
