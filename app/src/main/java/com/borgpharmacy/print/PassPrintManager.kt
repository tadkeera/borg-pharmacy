package com.borgpharmacy.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import com.borgpharmacy.R
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.borgArabicName
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

class PassPrintManager(private val context: Context) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun printPass(company: Company, representative: Representative, visit: Visit) {
        val html = buildPassHtml(company, representative, visit)
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build()
                printManager.print(
                    "Borg_${company.name}_${representative.name}",
                    view.createPrintDocumentAdapter("Borg Pharmacy Visit Pass"),
                    attributes,
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun buildPassHtml(company: Company, representative: Representative, visit: Visit): String {
        val logo = logoDataUri()
        val day = visit.date.dayOfWeek.borgArabicName()
        val date = visit.date.format(formatter)
        val shift = visit.shift.arabicName
        return """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8" />
                <style>
                    @page { size: 80mm auto; margin: 0; }
                    body { width: 80mm; margin: 0; padding: 4mm; font-family: Arial, sans-serif; color: #000; box-sizing: border-box; }
                    .center { text-align: center; }
                    img { width: 34mm; height: 34mm; object-fit: contain; margin: 0 auto 2mm auto; display: block; }
                    h1 { font-size: 17pt; margin: 0; color: #0E4D8F; }
                    h2 { font-size: 13pt; margin: 1mm 0 3mm; }
                    .row { border-top: 1px dashed #333; padding: 2mm 0; font-size: 12pt; }
                    .critical { font-size: 20pt; font-weight: 900; color: #000; line-height: 1.35; }
                    .label { font-weight: 700; }
                    .footer { margin-top: 3mm; font-size: 9pt; color: #333; }
                </style>
            </head>
            <body>
                <div class="center">
                    <img src="$logo" alt="Borg Pharmacy" />
                    <h1>صيدلية برج الأطباء</h1>
                    <h2>Pharmacy Administration</h2>
                </div>
                <div class="row"><span class="label">المندوب:</span> ${representative.name}</div>
                <div class="row"><span class="label">الشركة:</span> ${company.name}</div>
                <div class="row critical center">$date</div>
                <div class="row critical center">$day</div>
                <div class="row critical center">$shift</div>
                <div class="footer center">هذا التصريح صالح للزيارة المحددة فقط</div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun logoDataUri(): String {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.borg_logo)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }
}
