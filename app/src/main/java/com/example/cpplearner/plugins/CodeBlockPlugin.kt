import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
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
            val language = codeBlock.info ?: "text"

            // Create custom code block view
            val codeBlockView = LayoutInflater.from(context)
                .inflate(R.layout.code_block_layout, null)

            // Set up the views
            codeBlockView.findViewById<TextView>(R.id.languageLabel).text = language
            codeBlockView.findViewById<TextView>(R.id.codeText).text = code

            // Set up copy button
            codeBlockView.findViewById<ImageButton>(R.id.copyButton).setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
            }

            // Create a SpannableStringBuilder and append the code
            val sb = SpannableStringBuilder()
            sb.append(code)

            // Apply the spans
            visitor.builder().append(sb)
        }
    }
}