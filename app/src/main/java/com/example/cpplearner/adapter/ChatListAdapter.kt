package com.example.cpplearner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cpplearner.R
import com.example.cpplearner.roomDB.Chat

class ChatListAdapter(
    var chats: List<Chat>,
    private val onChatSelected: (Long) -> Unit,
    private val onChatLongPressed: (Long) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val summaryText: TextView = view.findViewById(R.id.chat_summary)
        val timestampText: TextView = view.findViewById(R.id.chat_timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_list_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.summaryText.text = chat.summary
        holder.timestampText.text = formatTimestamp(chat.timestamp)
        holder.itemView.setOnClickListener { onChatSelected(chat.chatId) }
        holder.itemView.setOnLongClickListener {
            onChatLongPressed(chat.chatId)
            true
        }
    }

    override fun getItemCount() = chats.size

    fun updateChats(newChats: List<Chat>) {
        chats = newChats
        notifyDataSetChanged()
    }


    private fun formatTimestamp(timestamp: Long): String {
        // Implement timestamp formatting as needed
        return android.text.format.DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
}