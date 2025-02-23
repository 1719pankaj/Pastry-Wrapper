package com.example.cpplearner.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.example.cpplearner.util.SimpleUniversalExtractor
import com.example.cpplearner.util.SimpleUniversalExtractor.Companion.BINARY_EXTENSIONS
import com.example.cpplearner.util.SimpleUniversalExtractor.Companion.IMAGE_EXTENSIONS
import com.example.cpplearner.util.SpeechRecognizerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainFragment : Fragment() {

    private lateinit var binding: FragmentMainBinding
    private lateinit var adapter: MainFragmentRecyclerAdapter
    private lateinit var gemini: Gemini
    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao
    private var currentChatId: Long = 0
    private var currentBitmap: Bitmap? = null
    private var currentFileName: String? = null
    private var extractedText: String = ""

    private val TAG = "MainFragment"
    private var currentMessageId: Long? = null

    private lateinit var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var pickFileLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Void?>

    private lateinit var speechRecognizerManager: SpeechRecognizerManager
    private lateinit var recordAudioPermissionLauncher: ActivityResultLauncher<String>

    private var isKeyboardOpen = false
    private var wasAtBottomBeforeKeyboard = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "messages-db").build()
        messageDao = db.messageDao()

        val rootView = binding.constraintLayout // Reference your root ConstraintLayout
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // Check if keyboard state changed
            if (keypadHeight > screenHeight * 0.15) { // Keyboard is open
                if (!isKeyboardOpen) {
                    // Store scroll position before keyboard opens
                    val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
                    wasAtBottomBeforeKeyboard = layoutManager.findLastCompletelyVisibleItemPosition() == adapter.itemCount - 1
                }
                isKeyboardOpen = true

                // If was at bottom, scroll after layout
                if (wasAtBottomBeforeKeyboard) {
                    binding.recyclerView.post {
                        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            } else {
                if (isKeyboardOpen) {
                    // Keyboard just closed, restore state
                    isKeyboardOpen = false
                    wasAtBottomBeforeKeyboard = false
                }
            }
        }

        initializeGemini()

        adapter = MainFragmentRecyclerAdapter(
            messages = listOf(),
            messageDao = messageDao,
            gemini = gemini,
            scope = lifecycleScope
        )

        setupRecyclerView()

        speechRecognizerManager = SpeechRecognizerManager(this)
        speechRecognizerManager.setOnTextUpdateListener { text ->
            binding.editTextMessage.setText(text)
            binding.editTextMessage.setSelection(text.length)
        }
        speechRecognizerManager.setOnListeningStateChangeListener { isListening ->
            updateMicButtonState(isListening)
        }

        binding.buttonMic.setOnClickListener {
            when {
                speechRecognizerManager.isListening() -> speechRecognizerManager.stopListening()
                checkAudioPermission() -> speechRecognizerManager.startListening()
                else -> recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.buttonSend.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                sendMessage()
            }
        }
        binding.buttonSend.setOnLongClickListener {
            findNavController().navigate(R.id.action_mainFragment_to_debugFragment)
            true
        }

        binding.imageViewMessage.setOnLongClickListener {
            currentBitmap = null
            binding.imageViewMessage.setImageBitmap(null)
            binding.imageViewMessage.visibility = View.GONE
            true
        }

        binding.textViewMessage.setOnLongClickListener {
            currentFileName = null
            extractedText = ""
            binding.textViewMessage.text = ""
            binding.textViewMessage.visibility = View.GONE
            true
        }
        binding.buttonPlus.setOnClickListener {
            showUploadMenu()
        }
        binding.modelSelectedLabel.setSelected(true)

        binding.modelSelectedLabel.text = ModelConfigProvider.getModels().find {
            it.modelName == getEncryptedPreferences().getString("SELECTED_MODEL", ModelConfigProvider.getDefaultModel().modelName)
        }?.displayName ?: ModelConfigProvider.getDefaultModel().displayName
        binding.modelPicker.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), binding.modelPicker)
            populateModelPickerMenu(popupMenu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                val selectedModel = ModelConfigProvider.getModels().find { it.displayName == menuItem.title }
                selectedModel?.let {
                    binding.modelSelectedLabel.text = it.displayName
                    val prefs = getEncryptedPreferences()
                    prefs.edit().putString("SELECTED_MODEL", it.modelName).apply()
                    initializeGemini()  // Reinitialize Gemini with the new model
                }
                true
            }

            popupMenu.show()
        }

        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // recorder permission initializer
        recordAudioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                speechRecognizerManager.startListening()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Audio permission is required for speech recognition",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

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

        takePictureLauncher =
            registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                if (bitmap != null) {
                    val croppedBitmap = cropToSquare(bitmap)
                    currentBitmap = bitmap  // Store the bitmap
                    binding.imageViewMessage.setImageBitmap(croppedBitmap)
                    binding.imageViewMessage.visibility = View.VISIBLE
                } else {
                    Log.d("Camera", "No picture taken")
                }
            }
        pickMediaLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    Log.d("PhotoPicker", "Selected URI: $uri")
                    readImage(uri)
                } else {
                    Log.d("PhotoPicker", "No media selected")
                }
            }
        pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Log.d("FilePicker", "Selected URI: $uri")
                val fileName = getFileName(uri)
                currentFileName = fileName  // Store the filename

                if (BINARY_EXTENSIONS.any { fileName.endsWith(it) }) {
                    Log.d("FilePicker", "Binary file selected")
                    Toast.makeText(
                        requireContext(),
                        "Unsupported binary file format",
                        Toast.LENGTH_SHORT
                    ).show()
                    clearAttachments()
                    return@registerForActivityResult
                }

                if (IMAGE_EXTENSIONS.any { fileName.endsWith(it) }) {
                    Log.d("FilePicker", "Image file selected")
                    readImage(uri)
                    return@registerForActivityResult
                }
                binding.textViewMessage.text = fileName
                binding.textViewMessage.visibility = View.VISIBLE
                extractTextFromFile(uri)
            } else {
                Log.d("FilePicker", "No file selected")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        initializeGemini()
        loadMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizerManager.destroy()
    }


    private fun showUploadMenu() {
        val popupMenu = PopupMenu(requireContext(), binding.buttonPlus)
        popupMenu.menuInflater.inflate(R.menu.upload_menu, popupMenu.menu)

        // Force show icons in popup menu
        try {
            val field = PopupMenu::class.java.getDeclaredField("mPopup")
            field.isAccessible = true
            val menuPopupHelper = field.get(popupMenu)
            val classPopupHelper = Class.forName("com.android.internal.view.menu.MenuPopupHelper")
            val setForceIcons = classPopupHelper.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
            setForceIcons.invoke(menuPopupHelper, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_camera -> {
                    takePictureLauncher.launch(null)
                    true
                }
                R.id.action_photo -> {
                    pickSingleImage()
                    true
                }
                R.id.action_file -> {
                    pickSingleFile()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun pickSingleImage() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun pickSingleFile() {
        pickFileLauncher.launch("*/*")
    }

    private fun clearAttachments(UIOnly: Boolean = false) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (UIOnly) {
                binding.imageViewMessage.setImageBitmap(null)
                binding.imageViewMessage.visibility = View.GONE
                binding.textViewMessage.text = ""
                binding.textViewMessage.visibility = View.GONE
            } else {
                currentBitmap = null
                currentFileName = null
                extractedText = ""
                binding.imageViewMessage.setImageBitmap(null)
                binding.imageViewMessage.visibility = View.GONE
                binding.textViewMessage.text = ""
                binding.textViewMessage.visibility = View.GONE
            }
        }
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
        return when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
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

    private fun readImage(uri: Uri) {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        val rotationAngle = getRotationAngle(uri)
        val rotatedBitmap = rotateBitmap(originalBitmap, rotationAngle)
        val croppedBitmap = cropToSquare(rotatedBitmap)
        currentBitmap = rotatedBitmap
        binding.imageViewMessage.setImageBitmap(croppedBitmap)
        binding.imageViewMessage.visibility = View.VISIBLE
    }

    private fun extractTextFromFile(uri: Uri) {
        binding.fileReadProgress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val extractor = SimpleUniversalExtractor(requireContext())
            extractedText = extractor.extractTextFromFile(uri)
            withContext(Dispatchers.Main) {
                binding.fileReadProgress.visibility = View.GONE
            }
        }

    }

    fun onChatDeleted(chatId: Long) {
        if (currentChatId == chatId) {
            currentChatId = 0
            adapter.updateMessages(emptyList(), binding.recyclerView) { }
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
        if (messageText.isBlank() && currentBitmap == null && extractedText.isBlank()) return

        withContext(Dispatchers.Main) {
            clearAttachments(true)
            binding.editTextMessage.text.clear()
        }

        val hasAttachment = currentBitmap != null || extractedText.isNotBlank()

        // Prepare the full message text including file content if present
        val fullMessageText = buildString {
            append(messageText)
            if (hasAttachment) {
                append("\n_____________________________\n")
                append("Attachment:\n")
                append(currentFileName)
                append("\n")
                append(extractedText)
            }
        }
        if (currentBitmap != null) {
            val imagePath = saveImageToInternalStorage(currentBitmap!!)
            val userMessage = Message(
                chatId = currentChatId,
                text = messageText,
                isUser = true,
                hasImage = true,
                hasAttachment = hasAttachment,
                attachmentFileName = if (hasAttachment) currentFileName else null,
                imagePath = imagePath
            )
            messageDao.insert(userMessage)
        } else {
            val userMessage = Message(
                text = messageText,
                thought = null,
                isUser = true,
                hasAttachment = hasAttachment,
                attachmentFileName = if (hasAttachment) currentFileName else null,
                chatId = currentChatId
            )
            messageDao.insert(userMessage)
        }
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

            val messageStream = if (currentBitmap != null) {
                gemini.sendMessageWithImageStream(fullMessageText, currentBitmap!!)
            } else {
                gemini.sendMessageStream(fullMessageText)
            }

            messageStream.collect { (text, thought) ->
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
        } finally {
            clearAttachments()
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap, filename: String? = null): String {
        // Generate unique filename with timestamp if not provided
        val timeStamp = System.currentTimeMillis()
        val imageFileName = filename ?: "IMG_${timeStamp}.jpg"

        // Get app's private directory
        val imagesDir = requireContext().getDir("images", Context.MODE_PRIVATE)
        if (!imagesDir.exists()) {
            imagesDir.mkdir()
        }

        // Create file
        val imageFile = File(imagesDir, imageFileName)

        try {
            // Save bitmap to file
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
            }
        } catch (e: Exception) {
            Log.e("MainFragment", "Error saving image", e)
            return ""
        }

        return imageFile.absolutePath
    }

    private fun updateMessages() {
        lifecycleScope.launch(Dispatchers.Main) {
            val messages = withContext(Dispatchers.IO) {
                messageDao.getMessagesForChat(currentChatId)
            }
            adapter.updateMessages(messages, binding.recyclerView) {
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

            if (messages.isNotEmpty()) {
                if (!::gemini.isInitialized) {
                    initializeGemini()
                }
                gemini.loadChatHistory(messages)
            }

            withContext(Dispatchers.Main) {
                adapter.updateMessages(messages, binding.recyclerView) {
                    if (messages.isNotEmpty()) {
                        binding.recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
            (activity as? MainActivity)?.updateNewChatButtonState(messages.isNotEmpty())
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateMicButtonState(isListening: Boolean) {
        binding.buttonMic.setImageResource(
            if (isListening) R.drawable.ic_mic_filled else R.drawable.ic_mic
        )
        binding.buttonMic.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                if (isListening) R.color.active_green else R.color.light_grey
            )
        )
    }

    private fun populateModelPickerMenu(popupMenu: PopupMenu) {
        val models = ModelConfigProvider.getModels()
        models.forEach { modelConfig ->
            popupMenu.menu.add(0, View.generateViewId(), 0, modelConfig.displayName)
        }
    }
}