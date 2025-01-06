package com.example.cpplearner.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

class SimpleUniversalExtractor(private val context: Context) {
    companion object {
        private const val TAG = "SimpleUniversalExtractor"
        private const val BUFFER_SIZE = 8192
        val BINARY_EXTENSIONS = listOf(
            ".mp3", ".mp4", ".avi", ".mov", ".wav", ".flac", ".zip", ".rar", ".7z", ".tar", ".gz",
            ".exe", ".dll", ".so")

        val IMAGE_EXTENSIONS = listOf(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".heic", ".heif"
        )

        val OFFICE_EXTENSIONS = listOf(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx"
        )

        val COMMON_ENCODINGS = listOf(
            "UTF-8",
            "UTF-16LE",
            "UTF-16BE",
            "ISO-8859-1",
            "windows-1252",
            "ASCII"
        )
    }

    init {
        // Initialize PDFBox
        PDFBoxResourceLoader.init(context)
    }

    fun extractTextFromFile(uri: Uri): String {
        try {
            // Get file name and check if it's a known binary format
            val fileName = getFileName(uri).lowercase()
            if (BINARY_EXTENSIONS.any { fileName.endsWith(it) } or IMAGE_EXTENSIONS.any { fileName.endsWith(it) }) {
                return "[Binary file: $fileName]"
            }

            if (OFFICE_EXTENSIONS.any { fileName.endsWith(it) }) {
                return try {
                    val extension = getFileExtension(getFileName(uri))
                    when (extension) {
                        "pdf" -> extractFromPDF(uri)
                        "doc", "docx" -> extractFromWord(uri)
                        "xls", "xlsx" -> extractFromExcel(uri)
                        else -> "Unsupported file type: $extension"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting text", e)
                    "Error extracting text: ${e.localizedMessage}"
                }
            }

            // Try to read the file with different encodings
            for (encoding in COMMON_ENCODINGS) {
                try {
                    val result = readWithEncoding(uri, encoding)
                    if (result.isNotEmpty() && isReadableText(result)) {
                        return cleanupText(result)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed with encoding $encoding", e)
                    continue
                }
            }

            // If all encodings fail, try raw byte reading
            return tryRawByteReading(uri)

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text", e)
            return "Error: ${e.localizedMessage}"
        }
    }

    private fun getFileExtension(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        Log.d(TAG+":getFileExtension", "File extension: $extension from path: $fileName")
        return extension
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && it.moveToFirst()) {
                    result = it.getString(nameIndex)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "Unknown"
    }

    private fun readWithEncoding(uri: Uri, encoding: String): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName(encoding)))
            val content = StringBuilder()
            var line: String? = null

            // Read line by line to handle large files
            while (reader.readLine()?.also { line = it } != null) {
                content.append(line).append("\n")
                // Basic size limit to prevent OOM
                if (content.length > 10_000_000) { // 10MB text limit
                    content.append("\n... (file too large, content truncated)")
                    break
                }
            }
            content.toString()
        } ?: throw Exception("Could not open file")
    }

    private fun tryRawByteReading(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = ByteArray(Math.min(inputStream.available(), 10_000_000)) // 10MB limit
            val read = inputStream.read(bytes)
            if (read > 0) {
                val rawText = String(bytes.copyOf(read), Charset.forName("UTF-8"))
                cleanupText(rawText)
            } else {
                "Empty file"
            }
        } ?: "Could not read file"
    }

    private fun isReadableText(text: String): Boolean {
        if (text.isEmpty()) return false

        // Calculate the ratio of printable characters
        val printableCount = text.count { it.isLetterOrDigit() || it.isWhitespace() || isPunctuation(it) }
        val ratio = printableCount.toFloat() / text.length

        // If more than 30% are readable characters, consider it text
        return ratio > 0.3
    }

    private fun isPunctuation(c: Char): Boolean {
        return c in setOf(',', '.', '!', '?', ';', ':', '(', ')', '[', ']', '{', '}',
            '<', '>', '/', '\\', '|', '-', '_', '+', '=', '@', '#', '$',
            '%', '^', '&', '*', '\'', '"', '`', '~')
    }

    private fun cleanupText(text: String): String {
        return text
            // Remove null characters
            .replace('\u0000', ' ')
            // Remove non-printable characters except newlines and tabs
            .replace(Regex("[^\\x20-\\x7E\\r\\n\\t]"), " ")
            // Normalize whitespace but preserve newlines
            .replace(Regex(" +"), " ")
            .replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")
            .trim()
    }

    private fun extractFromPDF(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bufferedInput = BufferedInputStream(inputStream, BUFFER_SIZE)
                PDDocument.load(bufferedInput).use { document ->
                    PDFTextStripper().getText(document)
                }
            } ?: throw Exception("Could not open PDF file")
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction failed", e)
            "Error extracting PDF: ${e.localizedMessage}"
        }
    }

    private fun extractFromWord(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bufferedInput = BufferedInputStream(inputStream, BUFFER_SIZE)
                XWPFDocument(bufferedInput).use { document ->
                    val textBuilder = StringBuilder()

                    // Extract paragraphs
                    document.paragraphs.forEach { paragraph ->
                        textBuilder.append(paragraph.text).append("\n")
                    }

                    // Extract tables
                    document.tables.forEach { table ->
                        table.rows.forEach { row ->
                            row.tableCells.forEach { cell ->
                                textBuilder.append(cell.text).append("\t")
                            }
                            textBuilder.append("\n")
                        }
                        textBuilder.append("\n")
                    }

                    textBuilder.toString()
                }
            } ?: throw Exception("Could not open Word file")
        } catch (e: Exception) {
            Log.e(TAG, "Word extraction failed", e)
            "Error extracting Word document: ${e.localizedMessage}"
        }
    }

    private fun extractFromExcel(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bufferedInput = BufferedInputStream(inputStream, BUFFER_SIZE)
                tryReadExcel(bufferedInput)
            } ?: throw Exception("Could not open Excel file")
        } catch (e: Exception) {
            Log.e(TAG, "Excel extraction failed", e)
            "Error extracting Excel file: ${e.localizedMessage}"
        }
    }

    private fun tryReadExcel(inputStream: InputStream): String {
        return try {
            // Try XLSX format first
            XSSFWorkbook(inputStream).use { workbook ->
                extractExcelContent(workbook)
            }
        } catch (e: Exception) {
            // If XLSX fails, try XLS format
            try {
                inputStream.reset()
                HSSFWorkbook(inputStream).use { workbook ->
                    extractExcelContent(workbook)
                }
            } catch (e2: Exception) {
                throw Exception("Neither XLSX nor XLS format could be read")
            }
        }
    }

    private fun extractExcelContent(workbook: Any): String {
        val content = StringBuilder()

        when (workbook) {
            is XSSFWorkbook -> {
                workbook.forEach { sheet ->
                    content.append("Sheet: ${sheet.sheetName}\n")
                    sheet.forEach { row ->
                        row.forEach { cell ->
                            when (cell.cellType) {
                                org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                    content.append(cell.numericCellValue)
                                org.apache.poi.ss.usermodel.CellType.STRING ->
                                    content.append(cell.stringCellValue)
                                org.apache.poi.ss.usermodel.CellType.BOOLEAN ->
                                    content.append(cell.booleanCellValue)
                                org.apache.poi.ss.usermodel.CellType.FORMULA ->
                                    content.append(cell.cellFormula)
                                else -> content.append("")
                            }
                            content.append("\t")
                        }
                        content.append("\n")
                    }
                    content.append("\n")
                }
            }
            is HSSFWorkbook -> {
                workbook.forEach { sheet ->
                    content.append("Sheet: ${sheet.sheetName}\n")
                    sheet.forEach { row ->
                        row.forEach { cell ->
                            when (cell.cellType) {
                                org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                    content.append(cell.numericCellValue)
                                org.apache.poi.ss.usermodel.CellType.STRING ->
                                    content.append(cell.stringCellValue)
                                org.apache.poi.ss.usermodel.CellType.BOOLEAN ->
                                    content.append(cell.booleanCellValue)
                                org.apache.poi.ss.usermodel.CellType.FORMULA ->
                                    content.append(cell.cellFormula)
                                else -> content.append("")
                            }
                            content.append("\t")
                        }
                        content.append("\n")
                    }
                    content.append("\n")
                }
            }
        }
        return content.toString()
    }
}