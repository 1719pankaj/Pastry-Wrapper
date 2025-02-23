package com.example.cpplearner.adapter

import CodeBlockPlugin
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cpplearner.R
import com.example.cpplearner.databinding.ItemMessageBinding
import com.example.cpplearner.gemini.Gemini
import com.example.cpplearner.roomDB.Message
import com.example.cpplearner.roomDB.MessageDao
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainFragmentRecyclerAdapter(private var messages: List<Message>,
                                  private val messageDao: MessageDao,
                                  private val gemini: Gemini,
                                  private val scope: CoroutineScope
) :
    RecyclerView.Adapter<MainFragmentRecyclerAdapter.MessageViewHolder>() {

    private lateinit var markwon: Markwon
    private var touchX: Float = 0f
    private var touchY: Float = 0f

    @SuppressLint("ClickableViewAccessibility")
    inner class MessageViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.textViewMessage.movementMethod = LinkMovementMethod.getInstance()
            binding.textViewMessage.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    touchX = event.rawX
                    touchY = event.rawY
                }
                false
            }

            binding.textViewMessage.setOnLongClickListener { view ->
                showContextMenu(view, adapterPosition)
                true
            }
        }


        fun bind(message: Message) {
            if (!::markwon.isInitialized) {
                markwon = Markwon.builder(binding.root.context)
                    .usePlugin(LinkifyPlugin.create())
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(binding.root.context))
                    .usePlugin(CodeBlockPlugin(binding.root.context))
                    .build()
            }

            // Set visibility and content for thought
            if (!message.isUser && !message.thought.isNullOrBlank()) {
                binding.textViewThought.visibility = View.VISIBLE
                binding.textViewThought.text = message.thought
            } else {
                binding.textViewThought.visibility = View.GONE
            }

            // Handle image display
            if (message.hasImage && !message.imagePath.isNullOrEmpty()) {
                binding.imageViewMessageItem.visibility = View.VISIBLE

                // Load and display image
                Glide.with(binding.root.context)
                    .load(message.imagePath)
                    .centerCrop()
                    .into(binding.imageViewMessageItem)
            } else {
                binding.imageViewMessageItem.visibility = View.GONE
            }

            //Handle Attachment display
            if (message.hasAttachment && !message.attachmentFileName.isNullOrEmpty()) {
                binding.textViewAttachment.visibility = View.VISIBLE
                binding.textViewAttachment.text = message.attachmentFileName
            } else {
                binding.textViewAttachment.visibility = View.GONE
            }

            // Apply markdown formatting - keep text rendering simple for streaming messages
            binding.textViewMessage.text = message.text
            if (!message.isUser) {
                markwon.setMarkdown(binding.textViewMessage, message.text)
                if(!message.thought.isNullOrBlank())
                    markwon.setMarkdown(binding.textViewThought, message.thought)
            }

            val constraintSet = ConstraintSet()
            constraintSet.clone(binding.root as ConstraintLayout)

            if (message.isUser) {
                constraintSet.connect(
                    binding.textViewMessage.id,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )
                constraintSet.clear(binding.textViewMessage.id, ConstraintSet.START)
                binding.textViewMessage.background = ContextCompat.getDrawable(binding.root.context, R.drawable.floating_bg)


                // Hide thought for user messages
                binding.textViewThought.visibility = View.GONE
            } else {
                constraintSet.connect(
                    binding.textViewMessage.id,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                constraintSet.clear(binding.textViewMessage.id, ConstraintSet.END)
                binding.textViewMessage.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.white))
            }

            constraintSet.applyTo(binding.root as ConstraintLayout)
        }
    }

    // Rest of your adapter implementation remains the same
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    private fun showContextMenu(view: View, position: Int) {
        val inflater = LayoutInflater.from(view.context)
        val message = messages[position]
        val popupView = inflater.inflate(R.layout.custom_popup_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val modelInfoOption = popupView.findViewById<TextView>(R.id.menu_model_name)
        val lineandbreak = popupView.findViewById<View>(R.id.lineandbreak)
        val menu_regenerate = popupView.findViewById<TextView>(R.id.menu_regenerate)

        popupView.findViewById<TextView>(R.id.menu_copy).setOnClickListener {
            val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", messages[position].text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(view.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_regenerate).setOnClickListener {
            regenerateMessage(position)
            popupWindow.dismiss()
        }

        if (!message.isUser) {
            modelInfoOption.visibility = View.VISIBLE
            val modelName = message.modelName ?: "Unknown Model"
            modelInfoOption.text = "Generated by: $modelName"
            // Make it non-clickable since it's just displaying info
            modelInfoOption.setOnClickListener(null)
            modelInfoOption.isClickable = false
        } else {
            modelInfoOption.visibility = View.GONE
            lineandbreak.visibility = View.GONE
            menu_regenerate.visibility = View.GONE
        }

        // Show popup at touch location
        popupWindow.showAtLocation(
            view,
            Gravity.NO_GRAVITY,
            touchX.toInt(),
            touchY.toInt()
        )

        // Optional: Adjust position if popup goes off screen
        popupView.viewTreeObserver.addOnGlobalLayoutListener {
            val screenWidth = view.context.resources.displayMetrics.widthPixels
            val screenHeight = view.context.resources.displayMetrics.heightPixels

            val popupWidth = popupView.width
            val popupHeight = popupView.height

            var x = touchX.toInt()
            var y = touchY.toInt()

            // Adjust X position if popup goes off screen
            if (x + popupWidth > screenWidth) {
                x = screenWidth - popupWidth
            }

            // Adjust Y position if popup goes off screen
            if (y + popupHeight > screenHeight) {
                y = screenHeight - popupHeight
            }

            popupWindow.update(x, y, -1, -1)
        }
    }

    private fun regenerateMessage(position: Int) {
        val currentMessage = messages[position]

        if (!currentMessage.isUser && position > 0) {
            val userMessage = messages[position - 1]

            scope.launch {
                try {
                    // Create a placeholder with the current model name
                    val updatedMessage = currentMessage.copy(
                        text = "Regenerating...",
                        modelName = gemini.modelName // Update to current model
                    )

                    // Update UI immediately with placeholder
                    messages = messages.toMutableList().apply {
                        set(position, updatedMessage)
                    }
                    notifyItemChanged(position)

                    // Update database with placeholder
                    messageDao.update(updatedMessage)

                    // Collect the stream of new response
                    var fullResponse = ""
                    gemini.sendMessageStream(userMessage.text)
                        .collect { partialResponse ->
                            fullResponse += partialResponse
                            val inProgressMessage = updatedMessage.copy(
                                text = fullResponse,
                                modelName = gemini.modelName
                            )

                            // Update UI with partial response
                            messages = messages.toMutableList().apply {
                                set(position, inProgressMessage)
                            }
                            notifyItemChanged(position)

                            // Update database with partial response
                            messageDao.update(inProgressMessage)
                        }
                } catch (e: Exception) {
                    // Handle error - revert to original message
                    messages = messages.toMutableList().apply {
                        set(position, currentMessage)
                    }
                    notifyItemChanged(position)
                    messageDao.update(currentMessage)

                    Log.e("MainFragmentAdapter", "Error regenerating message", e)
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateMessages(newMessages: List<Message>, recyclerView: RecyclerView, onMessagesUpdated: () -> Unit) {
        val wasAtBottom = (recyclerView.layoutManager as LinearLayoutManager)
            .findLastCompletelyVisibleItemPosition() == messages.size - 1

        messages = newMessages
        notifyDataSetChanged()

        if (messages.isNotEmpty() && wasAtBottom) {
            recyclerView.post {
                recyclerView.scrollToPosition(messages.size - 1)
            }
        }
        onMessagesUpdated()
    }


    fun updateLastMessage(message: Message) {
        if (messages.isNotEmpty()) {
            messages = messages.toMutableList().also {
                it[it.lastIndex] = message
            }
            notifyItemChanged(messages.lastIndex)
        }
    }
}