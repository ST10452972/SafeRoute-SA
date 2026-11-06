package com.example.opsc6312_poe

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.json.JSONObject

data class EmergencyContact(val id: String, val name: String, val phone: String, val waitingToSync: Boolean = false)

data class AppState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val firebaseReady: Boolean = false,
    val resources: List<SafetyResource> = emptyList(),
    val contacts: List<EmergencyContact> = emptyList()
)

class SafeRouteViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val firebaseReady = FirebaseApp.initializeApp(application) != null
    private val auth: FirebaseAuth? get() = if (firebaseReady) FirebaseAuth.getInstance() else null
    private val firestore: FirebaseFirestore? get() = if (firebaseReady) FirebaseFirestore.getInstance() else null
    private val outbox = OfflineQueue(application)
    private var contactsListener: ListenerRegistration? = null
    private val safetyResourceApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SafetyResourceApi::class.java)

    init {
        _state.value = AppState(signedIn = auth?.currentUser != null, firebaseReady = firebaseReady)
        auth?.currentUser?.let { user ->
            scheduleOutboxSync(application)
            observeEmergencyContacts(user.uid)
        }
    }

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
                    "fullName" to name.trim(), "email" to email.trim(), "language" to LocaleHelper.selected(getApplication()), "locationSharing" to true,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))?.await()
                refreshMessagingToken(result.user!!.uid)
                scheduleOutboxSync(getApplication())
                observeEmergencyContacts(result.user!!.uid)
                update(signedIn = true, message = if (register) "Account created securely." else "Welcome back.")
            } catch (e: Exception) { Log.e("SafeRoute", "Authentication failed", e); update(message = e.message ?: "Authentication failed.") }
            finally { update(busy = false) }
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        val service = auth ?: run { update(message = "Firebase is not configured. Add google-services.json first."); return }
        viewModelScope.launch {
            update(busy = true, message = null)
            try {
                val result = service.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                val user = result.user ?: error("Google sign-in did not return an account.")
                firestore?.collection("users")?.document(user.uid)?.set(mapOf(
                    "fullName" to (user.displayName ?: "SafeRoute member"), "email" to (user.email ?: ""),
                    "language" to LocaleHelper.selected(getApplication()), "locationSharing" to false
                ), com.google.firebase.firestore.SetOptions.merge())?.await()
                refreshMessagingToken(user.uid)
                scheduleOutboxSync(getApplication())
                observeEmergencyContacts(user.uid)
                update(signedIn = true, message = "Signed in with Google.")
            } catch (e: Exception) { Log.e("SafeRoute", "Google sign-in failed", e); update(message = e.localizedMessage ?: "Google sign-in failed.") }
            finally { update(busy = false) }
        }
    }

    private fun refreshMessagingToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            firestore?.collection("users")?.document(uid)?.set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                ?.addOnFailureListener { Log.w("SafeRoute", "Could not save notification token", it) }
        }.addOnFailureListener { Log.w("SafeRoute", "Could not retrieve notification token", it) }
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

    fun saveEmergencyContact(name: String, phone: String) {
        val error = Validation.required("Name", name) ?: Validation.southAfricanPhone(phone)
        if (error != null) { update(message = error); return }
        saveRecord("emergencyContacts", mapOf("name" to name.trim(), "phone" to phone.trim()), "Contact")
    }
    fun reportIncident(category: String, description: String) {
        if (category.isBlank() || description.isBlank()) { update(message = "Category and description are required."); return }
        val user = auth?.currentUser ?: run { update(message = "Please sign in first."); return }
        val payload = mutableMapOf("category" to category.trim(), "description" to description.trim(), "status" to "submitted")
        try { enqueueRecord(user.uid, "incidents", payload, "Incident report") }
        catch (e: Exception) { Log.e("SafeRoute", "Incident could not be queued", e); update(message = "Incident report could not be saved on this device.") }
    }
    fun triggerSos() = saveRecord("sos", mapOf("status" to "active", "message" to "SOS activated"), "SOS event")

    private fun saveRecord(collection: String, values: Map<String, String>, label: String) {
        val firstEmpty = values.entries.firstOrNull { it.value.trim().isEmpty() }
        if (firstEmpty != null) { update(message = "${firstEmpty.key.replaceFirstChar { it.uppercase() }} is required."); return }
        val user = auth?.currentUser ?: run { update(message = "Please sign in first."); return }
        viewModelScope.launch {
            update(busy = true, message = null)
            try {
                enqueueRecord(user.uid, collection, values, label)
                if (collection == "emergencyContacts") refreshPendingContacts(user.uid)
            }
            catch (e: Exception) { Log.e("SafeRoute", "Firestore write failed", e); update(message = e.message ?: "$label could not be saved.") }
            finally { update(busy = false) }
        }
    }

    private fun enqueueRecord(uid: String, collection: String, values: Map<String, String>, label: String) {
        outbox.enqueue(uid, collection, values)
        scheduleOutboxSync(getApplication())
        update(message = "$label saved on this device and queued to sync.")
    }

    private fun observeEmergencyContacts(uid: String) {
        contactsListener?.remove()
        contactsListener = firestore?.collection("emergencyContacts")
            ?.whereEqualTo("userId", uid)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SafeRoute", "Could not load emergency contacts", error)
                    refreshPendingContacts(uid)
                    return@addSnapshotListener
                }
                val synced = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val name = document.getString("name")?.trim().orEmpty()
                    val phone = document.getString("phone")?.trim().orEmpty()
                    if (name.isBlank() || phone.isBlank()) null
                    else EmergencyContact(document.id, name, phone)
                }
                setContactsIncludingPending(uid, synced)
            }
    }

    private fun refreshPendingContacts(uid: String) {
        setContactsIncludingPending(uid, _state.value.contacts.filterNot { it.waitingToSync })
    }

    private fun setContactsIncludingPending(uid: String, synced: List<EmergencyContact>) {
        val pending = outbox.pending(uid)
            .filter { it.collection == "emergencyContacts" }
            .mapNotNull { record ->
                val data = JSONObject(record.payload)
                val name = data.optString("name").trim()
                val phone = data.optString("phone").trim()
                if (name.isBlank() || phone.isBlank()) null
                else EmergencyContact(record.id, name, phone, waitingToSync = true)
            }
        val merged = (synced + pending).distinctBy { it.id }.sortedBy { it.name.lowercase() }
        _state.value = _state.value.copy(contacts = merged)
    }

    fun findSafetyResources(place: String) {
        if (place.isBlank()) { update(message = "Enter a South African town or suburb."); return }
        viewModelScope.launch {
            update(busy = true, message = null)
            try {
                val results = safetyResourceApi.search(query = "police station, ${place.trim()}, South Africa")
                _state.value = _state.value.copy(
                    resources = results,
                    message = if (results.isEmpty()) "No matching stations found. Try another town or spelling." else "Found ${results.size} matching result(s). Results may be incomplete."
                )
            } catch (e: Exception) {
                Log.e("SafeRoute", "Safety resource REST request failed", e)
                update(message = "Safety resource search failed. Check your connection and try again.")
            } finally { update(busy = false) }
        }
    }

    fun signOut() {
        contactsListener?.remove()
        contactsListener = null
        auth?.signOut()
        viewModelScope.launch {
            try { CredentialManager.create(getApplication()).clearCredentialState(ClearCredentialStateRequest()) }
            catch (e: Exception) { Log.w("SafeRoute", "Could not clear Google credential state", e) }
            _state.value = AppState(firebaseReady = firebaseReady)
        }
    }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }
    private fun update(busy: Boolean = _state.value.busy, signedIn: Boolean = _state.value.signedIn, message: String? = _state.value.message) { _state.value = _state.value.copy(busy = busy, signedIn = signedIn, message = message) }

    override fun onCleared() {
        contactsListener?.remove()
        contactsListener = null
        super.onCleared()
    }
}
