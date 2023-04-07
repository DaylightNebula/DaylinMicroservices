
// Code originally by LongClipeus (link: https://gist.github.com/LongClipeus/84db46e7d9714f67c4cbc40a67c8be1e)

package daylightnebula.daylinmicroservices.filesystem

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object HashUtils {

    const val STREAM_BUFFER_LENGTH = 1024

    // get checksum functions
    fun getCheckSumFromFile(digest: MessageDigest, filePath: String): String = getChecksum(digest, FileInputStream(File(filePath)))
    fun getCheckSumFromFile(digest: MessageDigest, file: File): String = getChecksum(digest, FileInputStream(file))
    fun getChecksumFromBytes(digest: MessageDigest, bytes: ByteArray): String = getChecksum(digest, ByteArrayInputStream(bytes))
    fun getChecksum(digest: MessageDigest, inputStream: InputStream): String {
        val byteArray = updateDigest(digest, inputStream).digest()
        inputStream.close()
        val hexCode = StringUtils.encodeHex(byteArray, true)
        return String(hexCode)
    }

    /**
     * Reads through an InputStream and updates the digest for the data
     *
     * @param digest The MessageDigest to use (e.g. MD5)
     * @param data Data to digest
     * @return the digest
     */
    private fun updateDigest(digest: MessageDigest, data: InputStream): MessageDigest {
        val buffer = ByteArray(STREAM_BUFFER_LENGTH)
        var read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)
        while (read > -1) {
            digest.update(buffer, 0, read)
            read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)
        }
        return digest
    }

}

object StringUtils {

    /**
     * Used to build output as Hex
     */
    private val DIGITS_LOWER =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    /**
     * Used to build output as Hex
     */
    private val DIGITS_UPPER =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data a byte[] to convert to Hex characters
     * @param toLowerCase `true` converts to lowercase, `false` to uppercase
     * @return A char[] containing hexadecimal characters in the selected case
     */
    fun encodeHex(data: ByteArray, toLowerCase: Boolean): CharArray {
        return encodeHex(data, if (toLowerCase) DIGITS_LOWER else DIGITS_UPPER)
    }

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data a byte[] to convert to Hex characters
     * @param toDigits the output alphabet (must contain at least 16 chars)
     * @return A char[] containing the appropriate characters from the alphabet
     *         For best results, this should be either upper- or lower-case hex.
     */
    fun encodeHex(data: ByteArray, toDigits: CharArray): CharArray {
        val l = data.size
        val out = CharArray(l shl 1)
        // two characters form the hex value.
        var i = 0
        var j = 0
        while (i < l) {
            out[j++] = toDigits[0xF0 and data[i].toInt() ushr 4]
            out[j++] = toDigits[0x0F and data[i].toInt()]
            i++
        }
        return out
    }

}