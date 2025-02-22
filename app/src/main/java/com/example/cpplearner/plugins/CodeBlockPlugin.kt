import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.cpplearner.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.spans.CodeBlockSpan
import org.commonmark.node.FencedCodeBlock

class CodeBlockPlugin(private val context: Context) : AbstractMarkwonPlugin() {

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(FencedCodeBlock::class.java) { configuration, props ->
            CodeBlockSpan(configuration.theme())
        }
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(FencedCodeBlock::class.java) { visitor, codeBlock ->
            val code = codeBlock.literal
            val language = codeBlock.info?.takeIf { it.isNotBlank() } ?: "text"

            // Create header with language and copy button
            val header = SpannableString("${language.replaceFirstChar{language[0].uppercase()}}  \u2398").apply {
                // Style language text
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    language.length,
                    0
                )
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.almost_black)),
                    0,
                    language.length,
                    0
                )
                setSpan(RelativeSizeSpan(0.8f), 0, language.length, 0)
                setSpan(TypefaceSpan("monospace"), 0, language.length, 0)

                // Style copy symbol
                val copyStart = language.length + 2
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.copy_button)),
                    copyStart,
                    copyStart + 1,
                    0
                )
                setSpan(CopyClickSpan(context, code), copyStart, copyStart + 1, 0)
            }

            val ssb = SpannableStringBuilder()
                .append(header)
                .append("\n")
                .append(code)

            // Use visitor's configuration theme instead of context theme
            val start = ssb.length - code.length - 1
            val end = ssb.length
            ssb.setSpan(CodeBlockSpan(visitor.configuration().theme()), start, end, 0)

            visitor.builder().append(ssb)
        }
    }

    private class CopyClickSpan(
        private val context: Context,
        private val code: String
    ) : ClickableSpan() {

        override fun onClick(widget: View) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("code", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
        }

        override fun updateDrawState(ds: TextPaint) {
            // No styling changes needed
        }
    }
}