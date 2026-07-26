package com.borgpharmacy.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.borgArabicName
import java.nio.charset.Charset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * طباعة مباشرة وسريعة عبر ESC/POS بدون فتح نافذة Android Print.
 *
 * يختار أول طابعة بلوتوث مقترنة باسم يشبه Printer/POS/Thermal/Receipt، وإذا لم يجد يستخدم
 * أول جهاز مقترن كخيار احتياطي. يجب أن تكون الطابعة مقترنة مسبقاً من إعدادات البلوتوث.
 */
class PassPrintManager(private val context: Context) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val serialPortUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun printPass(company: Company, representative: Representative, visit: Visit): Boolean {
        val printer = findPairedPrinter() ?: return false
        val socket = printer.createRfcommSocketToServiceRecord(serialPortUuid)
        return try {
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            socket.connect()
            socket.outputStream.use { outputStream ->
                outputStream.write(buildPassBytes(company, representative, visit))
                outputStream.flush()
            }
            true
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findPairedPrinter(): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val devices = runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        if (devices.isEmpty()) return null
        val printerNameRegex = Regex("printer|pos|thermal|receipt|mpt|rpp|xp|طابعة", RegexOption.IGNORE_CASE)
        return devices.firstOrNull { device -> printerNameRegex.containsMatchIn(device.name.orEmpty()) }
            ?: devices.firstOrNull()
    }

    private fun buildPassBytes(company: Company, representative: Representative, visit: Visit): ByteArray = buildBytes {
        initialize()
        selectArabicCodePage()
        alignCenter()
        setTextBold()
        setTextSize(2, 2)
        writeLine("صيدلية برج الأطباء")
        setTextSize(1, 1)
        writeLine("Pharmacy Administration")
        writeLine("--------------------------------")

        alignRight()
        setTextBold(false)
        setTextSize(1, 1)
        writeLine("المندوب: ${representative.name}")
        writeLine("الشركة: ${company.name}")
        writeLine("--------------------------------")

        alignCenter()
        setTextBold()
        setTextSize(2, 2)
        writeLine(visit.date.format(formatter))
        writeLine(visit.date.dayOfWeek.borgArabicName())
        writeLine(visit.shift.arabicName)

        writeLine("--------------------------------")
        setTextSize(1, 1)
        setTextBold(false)
        writeLine("هذا التصريح صالح للزيارة المحددة فقط")
        feedLines(4)
        cutPaper()
    }
}

class EscPosBuilder {
    private val buffer = mutableListOf<Byte>()
    private val arabicCharset: Charset = runCatching { Charset.forName("CP1256") }.getOrDefault(Charsets.UTF_8)

    fun initialize(): EscPosBuilder {
        buffer.addAll(bytes(0x1B, 0x40)) // ESC @
        return this
    }

    fun selectArabicCodePage(): EscPosBuilder {
        // ESC t n. أغلب الطابعات الحرارية تدعم Windows-1256 على code page 22.
        buffer.addAll(bytes(0x1B, 0x74, 0x16))
        return this
    }

    fun alignCenter(): EscPosBuilder {
        buffer.addAll(bytes(0x1B, 0x61, 0x01))
        return this
    }

    fun alignRight(): EscPosBuilder {
        buffer.addAll(bytes(0x1B, 0x61, 0x02))
        return this
    }

    fun setTextBold(bold: Boolean = true): EscPosBuilder {
        buffer.addAll(bytes(0x1B, 0x45, if (bold) 0x01 else 0x00))
        return this
    }

    fun setTextSize(width: Int, height: Int): EscPosBuilder {
        val safeWidth = width.coerceIn(1, 8)
        val safeHeight = height.coerceIn(1, 8)
        val size = ((safeWidth - 1) shl 4) or (safeHeight - 1)
        buffer.addAll(bytes(0x1D, 0x21, size))
        return this
    }

    fun writeLine(text: String): EscPosBuilder {
        buffer.addAll(text.toByteArray(arabicCharset).toList())
        buffer.add(0x0A.toByte())
        return this
    }

    fun feedLines(count: Int): EscPosBuilder {
        buffer.addAll(bytes(0x1B, 0x64, count.coerceIn(0, 10)))
        return this
    }

    fun cutPaper(): EscPosBuilder {
        buffer.addAll(bytes(0x1D, 0x56, 0x42, 0x00))
        return this
    }

    fun build(): ByteArray = buffer.toByteArray()

    private fun bytes(vararg values: Int): List<Byte> = values.map { it.toByte() }
}

inline fun buildBytes(block: EscPosBuilder.() -> Unit): ByteArray = EscPosBuilder().apply(block).build()
