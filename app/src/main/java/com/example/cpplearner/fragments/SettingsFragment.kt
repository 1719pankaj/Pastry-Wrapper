package com.example.cpplearner.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.cpplearner.R
import com.example.cpplearner.databinding.FragmentSettingsBinding
import com.example.cpplearner.roomDB.AppDatabase
import com.example.cpplearner.gemini.Gemini
import com.example.cpplearner.provider.ModelConfigProvider
import com.google.ai.client.generativeai.type.InvalidAPIKeyException
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

        addRadioButtons(18)

        return binding.root
    }

    private fun addRadioButtons(number: Int) {
        val radioGroup = binding.radioGroup
        radioGroup.removeAllViews()

        // Get current selected model from SharedPreferences
        val currentModel = sharedPreferences.getString("SELECTED_MODEL", ModelConfigProvider.getDefaultModel().modelName)

        ModelConfigProvider.getModels().forEach { modelConfig ->
            val radioButtonLayout = layoutInflater.inflate(R.layout.item_model_radio, radioGroup, false)
            val geminiRadioButton = radioButtonLayout.findViewById<RadioButton>(R.id.geminiRadioButton)
            val geminiNameTextView = radioButtonLayout.findViewById<TextView>(R.id.geminiNameTextView)
            val geminiPricingDetailsTextView = radioButtonLayout.findViewById<TextView>(R.id.geminiPricingDetailsTextView)
            val geminiRateLimitsDetailsTextView = radioButtonLayout.findViewById<TextView>(R.id.geminiRateLimitsDetailsTextView)
            val geminiKnowledgeCutoffDetailsTextView = radioButtonLayout.findViewById<TextView>(R.id.geminiKnowledgeCutoffDetailsTextView)

            // Remove the RadioButton from its parent
            (geminiRadioButton.parent as? ViewGroup)?.removeView(geminiRadioButton)

            // Add RadioButton directly to RadioGroup first
            radioGroup.addView(geminiRadioButton)

            // Then add the layout with the remaining views
            radioGroup.addView(radioButtonLayout)

            geminiRadioButton.id = View.generateViewId()
            geminiRadioButton.text = modelConfig.displayName
            geminiRadioButton.isChecked = modelConfig.modelName == currentModel

            geminiNameTextView.text = modelConfig.modelName
            geminiPricingDetailsTextView.text = "${modelConfig.inputPricing}\n${modelConfig.outputPricing}"
            geminiRateLimitsDetailsTextView.text = modelConfig.rateLimits
            geminiKnowledgeCutoffDetailsTextView.text = modelConfig.knowledgeCutoff

            geminiRadioButton.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    sharedPreferences.edit()
                        .putString("SELECTED_MODEL", modelConfig.modelName)
                        .apply()
                }
            }
        }
    }

    private fun saveApiKey(apiKey: String) {
        lifecycleScope.launch {
            val isValid = validateApiKey(apiKey, "gemini-2.0-flash-exp")
            kotlinx.coroutines.delay(1300)
            if (isValid) {
                sharedPreferences.edit().putString("GEMINI_API_KEY", apiKey).apply()
                findNavController().navigate(R.id.action_settingsFragment_to_mainFragment)
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