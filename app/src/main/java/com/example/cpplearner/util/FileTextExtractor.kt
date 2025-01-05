package com.example.cpplearner.util

import android.content.Context
import android.net.Uri
import android.util.Log
//import org.apache.poi.hwpf.HWPFDocument
//import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import java.util.Scanner
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.Jsoup
import com.opencsv.CSVReader
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor

class FileTextExtractor(private val context: Context) {

    fun extractTextFromFile(uri: Uri): String {
        var extractedText = ""

        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return "Unable to open file."
            val mimeType = context.contentResolver.getType(uri)

            extractedText = when {
                mimeType?.startsWith("text/") == true -> readTextFile(inputStream)
                mimeType == "application/pdf" -> extractTextFromPdf(inputStream)
//                mimeType == "application/msword" -> extractTextFromDoc(inputStream)
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> extractTextFromDocx(inputStream)
                mimeType == "application/json" -> extractTextFromJson(inputStream)
                mimeType == "text/html" -> extractTextFromHtml(inputStream)
                mimeType?.endsWith("xml") == true -> extractTextFromXml(inputStream)
                mimeType == "text/csv" -> extractTextFromCsv(inputStream)
                else -> fallbackExtract(inputStream)
            }

        } catch (e: Exception) {
            Log.e("FileTextExtractor", "Error extracting text", e)
            extractedText = "Error extracting text: ${e.localizedMessage}"
        }

        return extractedText
    }

    private fun readTextFile(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }

    private fun extractTextFromPdf(inputStream: InputStream): String {
        val reader = PdfReader(inputStream)
        val pageCount = reader.numberOfPages
        val text = StringBuilder()
        for (i in 1..pageCount) {
            text.append(PdfTextExtractor.getTextFromPage(reader, i))
        }
        reader.close()
        return text.toString()
    }

//    private fun extractTextFromDoc(inputStream: InputStream): String {
//        val doc = HWPFDocument(inputStream)
//        val extractor = WordExtractor(doc)
//        val text = extractor.text
//        doc.close()
//        return text
//    }

    private fun extractTextFromDocx(inputStream: InputStream): String {
        val docx = XWPFDocument(inputStream)
        val text = docx.paragraphs.joinToString(separator = "\n") { it.text }
        docx.close()
        return text
    }

    private fun extractTextFromJson(inputStream: InputStream): String {
        val json = inputStream.bufferedReader().use { it.readText() }
        return try {
            val jsonObject = JSONObject(json)
            jsonObject.toString(2)
        } catch (e: Exception) {
            val jsonArray = JSONArray(json)
            jsonArray.toString(2)
        }
    }

    private fun extractTextFromHtml(inputStream: InputStream): String {
        val html = inputStream.bufferedReader().use { it.readText() }
        return Jsoup.parse(html).text()
    }

    private fun extractTextFromXml(inputStream: InputStream): String {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream)
        document.documentElement.normalize()
        return document.documentElement.textContent
    }

    private fun extractTextFromCsv(inputStream: InputStream): String {
        val csvReader = CSVReader(InputStreamReader(inputStream))
        val lines = csvReader.readAll()
        csvReader.close()
        return lines.joinToString(separator = "\n") { it.joinToString(separator = ", ") }
    }

    private fun fallbackExtract(inputStream: InputStream): String {
        return try {
            val scanner = Scanner(inputStream).useDelimiter("\\A")
            if (scanner.hasNext()) scanner.next() else "Unable to extract content."
        } catch (e: Exception) {
            "Unsupported file type or unreadable content."
        }
    }
}
