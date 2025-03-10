package com.example.cpplearner.fragments

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.cpplearner.R
import com.example.cpplearner.databinding.DialogModelInfoBinding
import com.example.cpplearner.databinding.FragmentSettingsBinding
import com.example.cpplearner.roomDB.AppDatabase
import com.example.cpplearner.gemini.Gemini
import com.example.cpplearner.provider.ModelConfig
import com.example.cpplearner.provider.ModelConfigProvider
import com.google.ai.client.generativeai.type.InvalidAPIKeyException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SettingsFragment : Fragment() {

    lateinit var binding: FragmentSettingsBinding
    private lateinit var db: AppDatabase
    private lateinit var gemini: Gemini
    private lateinit var sharedPreferences: SharedPreferences
    

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "app-database").build()

        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            "secret_shared_prefs",
            masterKeyAlias,
            requireContext(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val apiKey = sharedPreferences.getString("GEMINI_API_KEY", null)
        if (apiKey.isNullOrEmpty()) {
            binding.scrollView2.visibility = View.GONE
        } else {
            binding.scrollView2.visibility = View.VISIBLE
        }

        binding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.saveButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.saveButton.setOnClickListener {
            val apiKey = binding.editTextMessage.text.toString()
            saveApiKey(apiKey)
        }

        binding.geminiWebButton.setOnClickListener {
            openAiStudio()
        }

        addModels()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Hide the ActionBar
        (activity as AppCompatActivity?)?.supportActionBar?.hide()
    }

    override fun onStop() {
        super.onStop()
        // Show the ActionBar again when the fragment is not visible
        (activity as AppCompatActivity?)?.supportActionBar?.show()
    }

    fun openAiStudio() {
        val url = "https://aistudio.google.com/apikey"
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }

    private fun addModels() {
        val modelsContainer = binding.modelsContainer
        modelsContainer.removeAllViews()

        val currentModel = sharedPreferences.getString("SELECTED_MODEL", ModelConfigProvider.getDefaultModel().modelName)

        ModelConfigProvider.getModels().forEach { modelConfig ->
            val modelView = layoutInflater.inflate(R.layout.item_model, modelsContainer, false)

            val modelNameLabel = modelView.findViewById<TextView>(R.id.modelNameLabel)
            val infoButton = modelView.findViewById<ImageButton>(R.id.infoButton)

            modelNameLabel.text = modelConfig.displayName

            infoButton.setOnClickListener {
                showModelInfoDialog(modelConfig)
            }

            modelsContainer.addView(modelView)
        }
    }

    private fun showModelInfoDialog(modelConfig: ModelConfig) {
        val dialog = Dialog(requireContext())
        val dialogBinding = DialogModelInfoBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Set dialog window to match parent width with margins
        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        with(dialogBinding) {
            modelNameTitle.text = modelConfig.displayName
            modelNameSubtitle.text = modelConfig.modelName
            pricingDetailsText.text = "${modelConfig.inputPricing}\n${modelConfig.outputPricing}"
            rateLimitsText.text = modelConfig.rateLimits
            knowledgeCutoffText.text = modelConfig.knowledgeCutoff
            specialFlagsText.text = modelConfig.specialFlags.joinToString(", ")

            closeButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun saveApiKey(apiKey: String) {
        lifecycleScope.launch {
            val isValid = validateApiKey(apiKey, "gemini-2.0-flash-exp")
            if (isValid) {
                sharedPreferences.edit().putString("GEMINI_API_KEY", apiKey).apply()
                binding.scrollView2.visibility = View.VISIBLE
                addModels() // Refresh models list
                lifecycleScope.launch {
                    delay(1300)
                    findNavController().navigate(R.id.action_settingsFragment_to_mainFragment)
                }
            }
        }
    }

    private suspend fun validateApiKey(apiKey: String, modelName: String): Boolean {
        binding.validationStatus.visibility = View.VISIBLE
        binding.validationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        binding.validationStatus.text = "Validating..."
        binding.validationProgressIndicator.visibility = View.VISIBLE
        if (apiKey.isEmpty()) {
            binding.validationStatus.text = "Dude WTF?"
            binding.validationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.failure_red))
            binding.validationProgressIndicator.visibility = View.GONE
            return false
        }
        return suspendCancellableCoroutine { continuation ->
            lifecycleScope.launch {
                try {
                    gemini = Gemini(apiKey, modelName)
                    val response = gemini.sendMessage("Validate API Key: $apiKey")
                    if (response != null) {
                        Toast.makeText(requireContext(), "API Key is valid", Toast.LENGTH_SHORT).show()
                        continuation.resume(true)
                        binding.validationStatus.text = "Validation successful"
                        binding.validationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success_green))
                    } else {
                        continuation.resume(false)
                        binding.validationStatus.text = "Validation failed"
                        binding.validationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.failure_red))
                    }
                } catch (e: InvalidAPIKeyException) {
                    Toast.makeText(requireContext(), "Invalid API Key: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.validationStatus.text = "Invalid API Key"
                    binding.validationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.failure_red))
                    continuation.resume(false)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Validation failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.validationStatus.text = "Validation failed"
                    binding.validationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.failure_red))
                    continuation.resumeWithException(e)
                } finally {
                    binding.validationProgressIndicator.visibility = View.GONE
                }
            }
        }
    }
}