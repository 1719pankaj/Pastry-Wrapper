package com.example.cpplearner.fragments

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.ExtractedText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.cpplearner.MainActivity
import com.example.cpplearner.R
import com.example.cpplearner.adapter.MainFragmentRecyclerAdapter
import com.example.cpplearner.databinding.FragmentMainBinding
import com.example.cpplearner.gemini.Gemini
import com.example.cpplearner.provider.ModelConfigProvider
import com.example.cpplearner.roomDB.AppDatabase
import com.example.cpplearner.roomDB.Chat
import com.example.cpplearner.roomDB.Message
import com.example.cpplearner.roomDB.MessageDao
import com.example.cpplearner.util.FileTextExtractor
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
    private var currentMessageId: Long? = null

    private lateinit var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var pickFileLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Void?>

    private var extractedText: String = ""

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
        binding.buttonSend.setOnLongClickListener {
            findNavController().navigate(R.id.action_mainFragment_to_debugFragment)
            true
        }
        binding.buttonCamera.setOnClickListener {
            openQuickTools(false)
            takePictureLauncher.launch(null)
        }
        binding.buttonImage.setOnClickListener {
            openQuickTools(false)
            pickSingleImage()
        }
        binding.buttonFile.setOnClickListener {
            openQuickTools(false)
            pickSingleFile()
        }
        binding.imageViewMessage.setOnLongClickListener{
            binding.imageViewMessage.setImageBitmap(null)
            binding.imageViewMessage.visibility = View.GONE
            true
        }
        binding.textViewMessage.setOnLongClickListener{
            extractedText = ""
            binding.textViewMessage.text = ""
            binding.textViewMessage.visibility = View.GONE
            true
        }
        binding.buttonPlus.setOnClickListener {
            openQuickTools(true)
        }
        binding.editTextMessage.setOnClickListener {
            openQuickTools(false)
        }

        return binding.root
    }



    private fun openQuickTools(boolean: Boolean) {
        binding.buttonPlus.visibility = if (boolean) View.GONE else View.VISIBLE
        binding.buttonFile.visibility = if (boolean) View.VISIBLE else View.GONE
        binding.buttonCamera.visibility = if (boolean) View.VISIBLE else View.GONE
        binding.buttonImage.visibility = if (boolean) View.VISIBLE else View.GONE
    }

    private fun pickSingleImage() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun pickSingleFile() {
        pickFileLauncher.launch("*/*")
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
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

    private fun getRotationAngle(uri: Uri): Int {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val exif = ExifInterface(inputStream!!)
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, angle: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(angle.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val dimension = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - dimension) / 2
        val y = (bitmap.height - dimension) / 2
        return Bitmap.createBitmap(bitmap, x, y, dimension, dimension)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (!::db.isInitialized) {
                db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "messages-db")
                    .build()
            }

            currentChatId = arguments?.getLong("chatId") ?: withContext(Dispatchers.IO) {
                val chatDao = db.chatDao()
                val newChat = Chat()
                chatDao.insertChat(newChat)
            }

            // Now that we have a valid chatId, load messages
            loadMessages()
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                val croppedBitmap = cropToSquare(bitmap)
                binding.imageViewMessage.setImageBitmap(croppedBitmap)
                binding.imageViewMessage.visibility = View.VISIBLE
            } else {
                Log.d("Camera", "No picture taken")
            }
        }
        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                val rotationAngle = getRotationAngle(uri)
                val rotatedBitmap = rotateBitmap(originalBitmap, rotationAngle)
                val croppedBitmap = cropToSquare(rotatedBitmap)
                binding.imageViewMessage.setImageBitmap(croppedBitmap)
                binding.imageViewMessage.visibility = View.VISIBLE
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }
        pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Log.d("FilePicker", "Selected URI: $uri")
                val fileName = getFileName(uri)
                binding.textViewMessage.text = fileName
                binding.textViewMessage.visibility = View.VISIBLE
                extractTextFromFile(uri)
            } else {
                Log.d("FilePicker", "No file selected")
            }
        }
    }

    private fun extractTextFromFile(uri: Uri) {
        val fileTextExtractor = FileTextExtractor(requireContext())
        extractedText = ""
        extractedText = fileTextExtractor.extractTextFromFile(uri)
    }

    override fun onResume() {
        super.onResume()
        initializeGemini()
        loadMessages()
    }

    fun onChatDeleted(chatId: Long) {
        if (currentChatId == chatId) {
            currentChatId = 0
            adapter.updateMessages(emptyList()) { }
            (activity as? MainActivity)?.let {
                it.updateNewChatButtonState(false)
                it.lifecycleScope.launch {
                    it.createNewChat()
                }
            }
        }
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
        val modelName =
            prefs.getString("SELECTED_MODEL", ModelConfigProvider.getDefaultModel().modelName)
                ?: ModelConfigProvider.getDefaultModel().modelName

        gemini = Gemini(apiKey, modelName)
    }

    private suspend fun generateSummary(chatId: Long) {
        withContext(Dispatchers.IO) {
            val messages = db.messageDao().getLastMessages(chatId)
            if (messages.size >= 2) {
                val conversationText = messages.takeLast(2)
                    .joinToString("\n") { "${if (it.isUser) "User" else "Assistant"}: ${it.text}" }

                val prompt =
                    "Summarize this conversation snippet in a brief (7-10 words):\n$conversationText"

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
                            binding.recyclerView.smoothScrollToPosition(
                                adapter?.itemCount?.minus(1) ?: 0
                            )
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

        val userMessage = Message(
            text = messageText,
            thought = null,
            isUser = true,
            chatId = currentChatId
        )
        messageDao.insert(userMessage)
        updateMessages()

        val botMessage = Message(
            text = "",
            thought = "",
            isUser = false,
            chatId = currentChatId,
            modelName = gemini.modelName
        )
        currentMessageId = messageDao.insert(botMessage)

        try {
            var finalText = ""
            var finalThought = ""

            gemini.sendMessageStream(messageText).collect { (text, thought) ->
                finalText += text
                finalThought += thought

                currentMessageId?.let { id ->
                    val updatedMessage = Message(
                        id = id.toInt(),
                        text = if (finalText.isNotBlank()) finalText.trimStart() else finalThought,
                        thought = if (finalText.isNotBlank()) finalThought.trimStart() else "",
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
            if (messages.count() < 3)
                generateSummary(currentChatId)
            (activity as? MainActivity)?.updateNewChatButtonState(messages.isNotEmpty())
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!::db.isInitialized)
                db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "messages-db")
                    .build()
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