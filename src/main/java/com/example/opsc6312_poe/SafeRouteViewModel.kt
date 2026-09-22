package com.example.opsc6312_poe

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class AppState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val firebaseReady: Boolean = false,
    val resources: List<SafetyResource> = emptyList()
)

class SafeRouteViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val firebaseReady = FirebaseApp.initializeApp(application) != null
    private val auth: FirebaseAuth? get() = if (firebaseReady) FirebaseAuth.getInstance() else null
    private val firestore: FirebaseFirestore? get() = if (firebaseReady) FirebaseFirestore.getInstance() else null
    private val api = Retrofit.Builder().baseUrl("https://nominatim.openstreetmap.org/")
        .addConverterFactory(GsonConverterFactory.create()).build().create(SafetyResourceApi::class.java)

    init { _state.value = AppState(signedIn = auth?.currentUser != null, firebaseReady = firebaseReady) }

    fun register(name: String, email: String, password: String) = performAuth(name, email, password, true)
    fun login(email: String, password: String) = performAuth("", email, password, false)

    private fun performAuth(name: String, email: String, password: String, register: Boolean) {
        val error = Validation.email(email) ?: Validation.password(password) ?: if (register) Validation.required("Name", name) else null
        if (error != null) { update(message = error); return }
        val service = auth ?: run { update(message = "Firebase is not configured. Add google-services.json first."); return }
        viewModelScope.launch {
            update(busy = true, message = null)
            try {
                val result = if (register) service.createUserWithEmailAndPassword(email.trim(), password).await()
                else service.signInWithEmailAndPassword(email.trim(), password).await()
                if (register) firestore?.collection("users")?.document(result.user!!.uid)?.set(mapOf(
                    "fullName" to name.trim(), "email" to email.trim(), "language" to "English", "locationSharing" to true,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))?.await()
                update(signedIn = true, message = if (register) "Account created securely." else "Welcome back.")
            } catch (e: Exception) { Log.e("SafeRoute", "Authentication failed", e); update(message = e.message ?: "Authentication failed.") }
            finally { update(busy = false) }
        }
    }

    fun saveSettings(name: String, language: String, locationSharing: Boolean) {
        if (Validation.required("Name", name) != null) { update(message = "Name is required."); return }
        val user = auth?.currentUser ?: run { update(message = "Please sign in first."); return }
        viewModelScope.launch {
            update(busy = true, message = null)
            try { firestore?.collection("users")?.document(user.uid)?.update(mapOf("fullName" to name.trim(), "language" to language, "locationSharing" to locationSharing))?.await(); update(message = "Settings saved.") }
            catch (e: Exception) { update(message = e.message ?: "Settings could not be saved.") }
            finally { update(busy = false) }
        }
    }

    fun saveEmergencyContact(name: String, phone: String) = saveRecord("emergencyContacts", mapOf("name" to name, "phone" to phone), "Contact")
    fun reportIncident(category: String, description: String) = saveRecord("incidents", mapOf("category" to category, "description" to description, "status" to "submitted"), "Incident")
    fun triggerSos() = saveRecord("sos", mapOf("status" to "active", "message" to "SOS activated"), "SOS event")

    private fun saveRecord(collection: String, values: Map<String, String>, label: String) {
        val firstEmpty = values.entries.firstOrNull { it.value.trim().isEmpty() }
        if (firstEmpty != null) { update(message = "${firstEmpty.key.replaceFirstChar { it.uppercase() }} is required."); return }
        val user = auth?.currentUser ?: run { update(message = "Please sign in first."); return }
        viewModelScope.launch {
            update(busy = true, message = null)
            try { firestore?.collection(collection)?.add(values + mapOf("userId" to user.uid, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))?.await(); update(message = "$label saved to Firestore.") }
            catch (e: Exception) { Log.e("SafeRoute", "Firestore write failed", e); update(message = e.message ?: "$label could not be saved.") }
            finally { update(busy = false) }
        }
    }

    fun findSafetyResources(place: String) {
        if (place.isBlank()) { update(message = "Enter a South African town or suburb."); return }
        viewModelScope.launch {
            update(busy = true, message = null)
            try { val result = api.search(query = "police station near $place, South Africa"); _state.value = _state.value.copy(resources = result, message = "Found ${result.size} safety resource(s).") }
            catch (e: Exception) { Log.e("SafeRoute", "REST API request failed", e); update(message = "Safety API unavailable. Check your internet connection.") }
            finally { update(busy = false) }
        }
    }

    fun signOut() { auth?.signOut(); _state.value = AppState(firebaseReady = firebaseReady) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }
    private fun update(busy: Boolean = _state.value.busy, signedIn: Boolean = _state.value.signedIn, message: String? = _state.value.message) { _state.value = _state.value.copy(busy = busy, signedIn = signedIn, message = message) }
}
