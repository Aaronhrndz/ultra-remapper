package com.aaronhrndz.ultraremapper.adb

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encapsulates the binary ADB protocol packet framing:
 * - command (4 bytes, e.g. "CNXN", "OPEN", "OKAY", "WRTE", "CLSE")
 * - arg0 (4 bytes, uint32)
 * - arg1 (4 bytes, uint32)
 * - data_length (4 bytes, uint32)
 * - data_crc32 (4 bytes, uint32)
 * - magic (4 bytes = command XOR 0xFFFFFFFF)
 */
data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray = ByteArray(0)
) {
    val dataLength: Int get() = data.size
    val magic: Int get() = command xor -1

    fun calculateChecksum(): Int {
        var sum = 0
        for (b in data) {
            sum += b.toInt() and 0xFF
        }
        return sum
    }

    fun writeTo(output: OutputStream) {
        val header = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(data.size)
        header.putInt(calculateChecksum())
        header.putInt(magic)
        output.write(header.array())
        if (data.isNotEmpty()) {
            output.write(data)
        }
        output.flush()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AdbMessage
        if (command != other.command) return false
        if (arg0 != other.arg0) return false
        if (arg1 != other.arg1) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + arg0
        result = 31 * result + arg1
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_LENGTH = 24

        // Standard ADB protocol 32-bit commands (little-endian characters)
        const val CMD_SYNC = 0x434e5953 // "SYNC"
        const val CMD_CNXN = 0x4e584e43 // "CNXN"
        const val CMD_OPEN = 0x4e45504f // "OPEN"
        const val CMD_OKAY = 0x59414b4f // "OKAY"
        const val CMD_CLSE = 0x45534c43 // "CLSE"
        const val CMD_WRTE = 0x45545257 // "WRTE"
        const val CMD_AUTH = 0x48545541 // "AUTH"

        const val AUTH_TYPE_TOKEN = 1
        const val AUTH_TYPE_SIGNATURE = 2
        const val AUTH_TYPE_RSAPUBLICKEY = 3

        const val CONNECT_VERSION = 0x01000000
        const val CONNECT_MAXDATA = 1024 * 1024

        fun readFrom(input: InputStream): AdbMessage {
            val headerBytes = ByteArray(HEADER_LENGTH)
            var read = 0
            while (read < HEADER_LENGTH) {
                val r = input.read(headerBytes, read, HEADER_LENGTH - read)
                if (r == -1) throw java.io.EOFException("End of stream reading ADB header")
                read += r
            }

            val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = buf.int
            val arg0 = buf.int
            val arg1 = buf.int
            val dataLen = buf.int
            val dataChecksum = buf.int
            val magic = buf.int

            if (cmd xor -1 != magic) {
                throw IllegalStateException("Invalid ADB magic header checksum")
            }

            val payload = ByteArray(dataLen)
            var pRead = 0
            while (pRead < dataLen) {
                val r = input.read(payload, pRead, dataLen - pRead)
                if (r == -1) throw java.io.EOFException("End of stream reading ADB payload")
                pRead += r
            }

            return AdbMessage(cmd, arg0, arg1, payload)
        }
    }
}
