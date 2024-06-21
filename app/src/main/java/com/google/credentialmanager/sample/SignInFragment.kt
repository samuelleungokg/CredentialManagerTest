/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.credentialmanager.sample

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.credentialmanager.sample.MainActivity.Companion.SP_NAME
import com.google.credentialmanager.sample.databinding.FragmentSignInBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SignInFragment : Fragment() {

    private lateinit var credentialManager: CredentialManager
    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!
    private lateinit var listener: SignInFragmentCallback

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as SignInFragmentCallback
        } catch (castException: ClassCastException) {
            /** The activity does not implement the listener.  */
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        credentialManager = CredentialManager.create(requireActivity())

        binding.signInWithSavedCredentials.setOnClickListener(signInWithSavedCredentials())
    }

    private fun signInWithSavedCredentials(): View.OnClickListener {
        return View.OnClickListener {

            lifecycleScope.launch {
                configureViews(View.VISIBLE, false)

                val data = getSavedCredentials()

                configureViews(View.INVISIBLE, true)

                data?.let {
                    sendSignInResponseToServer()
                    listener.showHome()
                }
            }
        }
    }

    private fun configureViews(visibility: Int, flag: Boolean) {
        configureProgress(visibility)
        binding.signInWithSavedCredentials.isEnabled = flag
    }

    private fun configureProgress(visibility: Int) {
        binding.textProgress.visibility = visibility
        binding.circularProgressIndicator.visibility = visibility
    }

    fun getIdsFromSharedPreferences(context: Context): List<String> {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences(MainActivity.SP_NAME, Context.MODE_PRIVATE)
        val idsJson = sharedPreferences.getString("ids", "[]")
        val idsArray = JSONArray(idsJson)

        val idsList = mutableListOf<String>()
        for (i in 0 until idsArray.length()) {
            idsList.add(idsArray.getString(i))
        }

        return idsList
    }

    fun createAllowCredentialsJsonString(context: Context): String {
        // Retrieve the list of IDs from SharedPreferences
        val ids = getIdsFromSharedPreferences(context)

        // Create a JSON array for allowCredentials
        val allowCredentialsArray = JSONArray()

        // Loop through the list of IDs and create the JSON objects
        for (id in ids) {
            val credentialObject = JSONObject().apply {
                put("transports", JSONArray(listOf("hybrid", "internal")))
                put("id", id)
                put("type", "public-key")
            }

            // Add the credential object to the allowCredentials array
            allowCredentialsArray.put(credentialObject)
        }

        // Return the JSON array as a string
        return allowCredentialsArray.toString()
    }

    private fun fetchAuthJsonFromServer(): String {
        val response =  requireContext().readFromAsset("AuthFromServer")
        val finalResponse = response
            .replace("<userVerification>", getUserVerificationFromRadio())
            .replace("<newAllowCredential>",  ",\"allowCredentials\" : ${createAllowCredentialsJsonString(requireContext())}")

        Log.d("asdf", "finalResponse: $finalResponse")

        return finalResponse
    }

    private fun getUserVerificationFromRadio(): String {
        return when {
            binding.uvPreferred.isChecked -> "preferred"
            binding.uvDiscouraged.isChecked -> "discouraged"
            binding.uvRequired.isChecked -> "required"
            else -> "preferred"
        }
    }

    private fun sendSignInResponseToServer(): Boolean {
        return true
    }

    private suspend fun getSavedCredentials(): String? {
        val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(fetchAuthJsonFromServer(), null)

        val getPasswordOption = GetPasswordOption()

        val result = try {
            credentialManager.getCredential(
                requireActivity(),
                GetCredentialRequest(
                    listOf(
                        getPublicKeyCredentialOption,
                        getPasswordOption
                    )
                )
            )
        } catch (e: Exception) {
            configureViews(View.INVISIBLE, true)
            Log.e("Auth", "getCredential failed with exception: " + e.message.toString())
            activity?.showErrorAlert(
                "An error occurred while authenticating through saved credentials. Check logs for additional details"
            )
            return null
        }

        if (result.credential is PublicKeyCredential) {
            val cred = result.credential as PublicKeyCredential
            DataProvider.setSignedInThroughPasskeys(true)
            return "Passkey: ${cred.authenticationResponseJson}"
        }
        if (result.credential is PasswordCredential) {
            val cred = result.credential as PasswordCredential
            DataProvider.setSignedInThroughPasskeys(false)
            return "Got Password - User:${cred.id} Password: ${cred.password}"
        }
        if (result.credential is CustomCredential) {
            //If you are also using any external sign-in libraries, parse them here with the utility functions provided.
        }

        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        configureProgress(View.INVISIBLE)
        _binding = null
    }

    interface SignInFragmentCallback {
        fun showHome()
    }
}
