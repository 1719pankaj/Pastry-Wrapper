package com.example.cpplearner

import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.cpplearner.adapter.ChatListAdapter
import com.example.cpplearner.databinding.ActivityMainBinding
import com.example.cpplearner.fragments.MainFragment
import com.example.cpplearner.gemini.Gemini
import com.example.cpplearner.roomDB.AppDatabase
import com.example.cpplearner.roomDB.Chat
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatListAdapter: ChatListAdapter
    private lateinit var db: AppDatabase
    lateinit var gemini: Gemini



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = findViewById(R.id.drawer_layout)

        // Calculate and set navigation drawer width (80% of screen width)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val displayMetrics = resources.displayMetrics
        val drawerWidth = (displayMetrics.widthPixels * 0.8).toInt()
        val params = navigationView.layoutParams as DrawerLayout.LayoutParams
        params.width = drawerWidth
        navigationView.layoutParams = params

        val navigationHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navigationHost.navController

        setupActionBar(navController)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "messages-db").build()

        // Handle pastry image click to open the drawer
        val pastryImage: ImageView = findViewById(R.id.pastry)
        pastryImage.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setupNavigation()
        setupNewChatButton()

        // Check if the encrypted API key exists
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "secret_shared_prefs",
            masterKeyAlias,
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val apiKey = sharedPreferences.getString("GEMINI_API_KEY", null)

        // Navigate based on the existence of the API key
        if (apiKey.isNullOrEmpty()) {
            navController.navigate(R.id.settingsFragment)
        }

        // Example of a call to a native method
        binding.sampleText.text = stringFromJNI()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch(Dispatchers.IO) {
            if (!::db.isInitialized)
                db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "messages-db").build()
            db.chatDao().deleteEmptyChats()
        }
    }

    private fun setupNavigation() {
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val menuContent = layoutInflater.inflate(R.layout.nav_header, navigationView, false)
        navigationView.addView(menuContent)

        // Update your view references
        val searchBox: EditText = menuContent.findViewById(R.id.search_box)
        val chatsRecyclerView: RecyclerView = menuContent.findViewById(R.id.chats_recycler_view)
        val settingsButton: ImageButton = menuContent.findViewById(R.id.settings_drawer)
        val userAccountButton: ImageButton = menuContent.findViewById(R.id.user_account_settings_drawer)

        // Call your existing setup methods with the new references
        setupChatList(chatsRecyclerView)
        setupSearchBox(searchBox)
        setupSettingsButtons(settingsButton, userAccountButton)
    }

    private fun setupActionBar(navController: NavController) {
        supportActionBar?.apply {
            setDisplayShowCustomEnabled(true)
            setDisplayShowTitleEnabled(false)
            setCustomView(R.layout.custom_action_bar)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.mainFragment -> {
                    // Set up action bar for mainFragment
                    supportActionBar?.customView?.findViewById<ImageView>(R.id.newChat)?.visibility = View.VISIBLE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    findViewById<ImageView>(R.id.pastry).setOnClickListener {
                        drawerLayout.openDrawer(GravityCompat.START)
                    }
                }
                R.id.settingsFragment -> {
                    // Set up action bar for settingsFragment
                    supportActionBar?.customView?.findViewById<ImageView>(R.id.newChat)?.visibility = View.INVISIBLE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    findViewById<ImageView>(R.id.pastry).setOnClickListener(null)
                }
                // Add more cases for other fragments as needed
                else -> {
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    findViewById<ImageView>(R.id.pastry).setOnClickListener {
                        drawerLayout.openDrawer(GravityCompat.START)
                    }
                }
            }
        }
    }

    private fun setupChatList(chatsRecyclerView: RecyclerView) {
        chatListAdapter = ChatListAdapter(emptyList(), { chatId ->
            lifecycleScope.launch {
                loadChat(chatId)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }, { chatId ->
            showDeleteChatDialog(chatId)
        })

        chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                reverseLayout = true  // Set to false to maintain natural order
                stackFromEnd = true   // Items will start from the bottom
            }
            adapter = chatListAdapter
        }

        // Optional: Add padding to bottom to ensure last item is fully visible
        chatsRecyclerView.clipToPadding = false

        lifecycleScope.launch {
            updateChatList()
        }
    }

    private fun setupNewChatButton() {
        val newChatButton = supportActionBar?.customView?.findViewById<ImageView>(R.id.newChat)
        updateNewChatButtonState(false)
        newChatButton?.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.Main) {
                    updateNewChatButtonState(false)
                }
                createNewChat()
            }
        }
    }

    suspend fun createNewChat() {
        try {
            val chatId = withContext(Dispatchers.IO) {
                val chatDao = db.chatDao()
                val newChat = Chat()
                chatDao.insertChat(newChat)
            }

            withContext(Dispatchers.Main) {
                val navController = findNavController(R.id.nav_host_fragment)
                navController.popBackStack()
                val bundle = Bundle().apply {
                    putLong("chatId", chatId)
                }
                navController.navigate(R.id.mainFragment, bundle)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating new chat", e)
        }
    }

    private fun setupSettingsButtons(settingsButton: ImageButton, userAccountButton: ImageButton) {
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val headerView = navigationView.getHeaderView(0)

//        val settingsButton = headerView.findViewById<ImageButton>(R.id.settings_drawer)
//        val userAccountButton = headerView.findViewById<ImageButton>(R.id.user_account_settings_drawer)

        val settingsClickListener = View.OnClickListener {
            val navController = findNavController(R.id.nav_host_fragment)
            if (navController.currentDestination?.id != R.id.settingsFragment) {
                navController.navigate(R.id.action_mainFragment_to_settingsFragment)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        settingsButton.setOnClickListener(settingsClickListener)
        userAccountButton.setOnClickListener(settingsClickListener)
    }

    private fun setupSearchBox(searchBox: EditText) {
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val headerView = navigationView.getHeaderView(0)
//        val searchBox: EditText = headerView.findViewById(R.id.search_box)

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Launch in a coroutine to handle database operations
                lifecycleScope.launch {
                    filterChats(s?.toString() ?: "")
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private suspend fun filterChats(query: String) {
        withContext(Dispatchers.IO) {
            // Always fetch the full list first
            val allChats = if (!::db.isInitialized) {
                db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "messages-db").build()
                db.chatDao().getChatsWithMessages()
            } else {
                db.chatDao().getChatsWithMessages()
            }

            // Then filter based on query
            val filteredChats = if (query.isEmpty()) {
                allChats
            } else {
                allChats.filter { chat ->
                    chat.summary?.contains(query, ignoreCase = true) == true
                }
            }

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                chatListAdapter.updateChats(filteredChats)
            }
        }
    }

    private fun showDeleteChatDialog(chatId: Long) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Sure you wanna delete this chat?")
            .setPositiveButton("Delete") { dialog, id ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.chatDao().deleteChat(chatId)
                    }
                    updateChatList()
                    notifyChatDeleted(chatId)
                }
            }
            .setNegativeButton("Cancel") { dialog, id ->
                dialog.dismiss()
            }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.black))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.black))
        }
        dialog.show()
    }

    private fun notifyChatDeleted(chatId: Long) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val mainFragment = navHostFragment.childFragmentManager.fragments.firstOrNull { it is MainFragment } as? MainFragment
        mainFragment?.onChatDeleted(chatId)
    }

    private suspend fun loadChat(chatId: Long) {
        withContext(Dispatchers.Main) {
            val navController = findNavController(R.id.nav_host_fragment)
            navController.popBackStack()
            val bundle = Bundle().apply {
                putLong("chatId", chatId)
            }
            navController.navigate(R.id.mainFragment, bundle)
        }
    }

    fun updateNewChatButtonState(enable: Boolean) {
        val newChatButton = supportActionBar?.customView?.findViewById<ImageView>(R.id.newChat)
        if(enable) {
            newChatButton?.visibility = View.VISIBLE

        } else {
            newChatButton?.visibility = View.GONE
        }
    }

    suspend fun updateChatList() {
        withContext(Dispatchers.IO) {
            if (!::db.isInitialized)
                db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "messages-db").build()
            val chatsWithoutSummary = db.chatDao().getChatsWithoutSummary()
            val nonEmptyChats = chatsWithoutSummary.filter { chat ->
                db.messageDao().getMessagesForChat(chat.chatId).isNotEmpty()
            }
            val chatsWithSummary = db.chatDao().getChatsWithMessages()
            val allChats = chatsWithSummary + nonEmptyChats
            withContext(Dispatchers.Main) {
                chatListAdapter.updateChats(allChats)
            }
        }
    }

    /**
     * A native method that is implemented by the 'cpplearner' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'cpplearner' library on application startup.
        init {
            System.loadLibrary("cpplearner")
        }
    }
}