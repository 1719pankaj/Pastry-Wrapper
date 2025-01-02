package com.example.cpplearner.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.cpplearner.MainActivity
import com.example.cpplearner.adapter.MainFragmentRecyclerAdapter
import com.example.cpplearner.databinding.FragmentMainBinding
import com.example.cpplearner.gemini.Gemini
import com.example.cpplearner.provider.ModelConfigProvider
import com.example.cpplearner.roomDB.AppDatabase
import com.example.cpplearner.roomDB.Chat
import com.example.cpplearner.roomDB.Message
import com.example.cpplearner.roomDB.MessageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainFragment : Fragment() {

    private lateinit var binding: FragmentMainBinding
    private lateinit var adapter: MainFragmentRecyclerAdapter
    private lateinit var gemini: Gemini
    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao
    private var currentChatId: Long = 0

    private val TAG = "MainFragment"
    private var currentMessageId: Long? = null  // Add this to track current message ID

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "messages-db").build()
        messageDao = db.messageDao()

        initializeGemini()

        adapter = MainFragmentRecyclerAdapter(
            messages = listOf(),
            messageDao = messageDao,
            gemini = gemini,
            scope = lifecycleScope
        )

        setupRecyclerView()

        binding.buttonSend.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                sendMessage()
            }
        }

        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (!::db.isInitialized) {
                db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "messages-db").build()
            }

            currentChatId = arguments?.getLong("chatId") ?: withContext(Dispatchers.IO) {
                val chatDao = db.chatDao()
                val newChat = Chat()
                chatDao.insertChat(newChat)
            }

            // Now that we have a valid chatId, load messages
            loadMessages()
        }
    }

    override fun onResume() {
        super.onResume()
        initializeGemini()
    }

    private fun getEncryptedPreferences(): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "secret_shared_prefs",
            masterKeyAlias,
            requireContext(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun initializeGemini() {
        val prefs = getEncryptedPreferences()
        val apiKey = prefs.getString("GEMINI_API_KEY", "") ?: ""
        val modelName = prefs.getString("SELECTED_MODEL", ModelConfigProvider.getDefaultModel().modelName) ?: ModelConfigProvider.getDefaultModel().modelName

        gemini = Gemini(apiKey, modelName)
    }

    private suspend fun generateSummary(chatId: Long) {
        withContext(Dispatchers.IO) {
            val messages = db.messageDao().getLastMessages(chatId)
            if (messages.size >= 2) {
                val conversationText = messages.takeLast(2)
                    .joinToString("\n") { "${if (it.isUser) "User" else "Assistant"}: ${it.text}" }

                val prompt = "Summarize this conversation snippet in a brief (7-10 words):\n$conversationText"

                try {
                    val summary = gemini.sendMessage(prompt) ?: "New Chat"
                    db.chatDao().updateChatSummary(chatId, summary)
                    // Update chat list in MainActivity
                    (activity as? MainActivity)?.let {
                        withContext(Dispatchers.Main) {
                            it.lifecycleScope.launch {
                                it.updateChatList()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainFragment", "Error generating summary", e)
                    db.chatDao().updateChatSummary(chatId, "New Chat")
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            // Remove reverseLayout as we want natural ordering
            reverseLayout = false
            stackFromEnd = true  // Keep this to start from bottom
        }

        binding.recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = this@MainFragment.adapter

            // Add this to maintain position when keyboard appears
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom) {
                    binding.recyclerView.postDelayed({
                        if ((adapter?.itemCount ?: 0) > 0) {
                            binding.recyclerView.smoothScrollToPosition(adapter?.itemCount?.minus(1) ?: 0)
                        }
                    }, 100)
                }
            }
        }
    }

    private suspend fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString()
        if (messageText.isBlank()) return

        withContext(Dispatchers.Main) {
            binding.editTextMessage.text.clear()
        }

        // Insert user message
        val userMessage = Message(
            text = messageText,
            isUser = true,
            chatId = currentChatId,
            modelName = null // User messages don't have a model
        )
        messageDao.insert(userMessage)
        updateMessages()

        // Create initial bot message
        val botMessage = Message(
            text = "",
            isUser = false,
            chatId = currentChatId,
            modelName = gemini.modelName // Store the current model name
        )
        currentMessageId = messageDao.insert(botMessage)

        var fullResponse = ""

        try {
            gemini.sendMessageStream(messageText).collect { partialResponse ->
                fullResponse += partialResponse
                currentMessageId?.let { id ->
                    val updatedMessage = Message(
                        id = id.toInt(),
                        text = fullResponse,
                        isUser = false,
                        chatId = currentChatId,
                        modelName = gemini.modelName
                    )
                    withContext(Dispatchers.IO) {
                        messageDao.update(updatedMessage)
                    }
                    updateMessages()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming", e)
        }
    }

    private fun updateMessages() {
        lifecycleScope.launch(Dispatchers.Main) {
            val messages = withContext(Dispatchers.IO) {
                messageDao.getMessagesForChat(currentChatId)
            }
            adapter.updateMessages(messages) {
                // Check if we're near the bottom before scrolling
                val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val totalItems = layoutManager.itemCount

                // If we're within 3 items of the bottom, scroll to the new message
                if (totalItems - lastVisibleItem <= 3) {
                    binding.recyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
            if(messages.count() < 3)
                generateSummary(currentChatId)
            (activity as? MainActivity)?.updateNewChatButtonState(messages.isNotEmpty())
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!::db.isInitialized)
                db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "messages-db").build()
            if (!::messageDao.isInitialized)
                messageDao = db.messageDao()
            val messages = messageDao.getMessagesForChat(currentChatId)
            withContext(Dispatchers.Main) {
                adapter.updateMessages(messages) {
                    if (messages.isNotEmpty()) {
                        binding.recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
            (activity as? MainActivity)?.updateNewChatButtonState(messages.isNotEmpty())
        }
    }
}