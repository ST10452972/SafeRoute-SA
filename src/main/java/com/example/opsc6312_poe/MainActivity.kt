package com.example.opsc6312_poe

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.example.opsc6312_poe.ui.theme.OPSC6312POETheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(LocaleHelper.wrap(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OPSC6312POETheme { SafeRouteApp() } }
    }
}

private enum class Screen { HOME, CONTACTS, INCIDENT, RESOURCES, SETTINGS }

private val AuthColorScheme = lightColorScheme(
    primary = Color(0xFF005A9C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFA),
    onPrimaryContainer = Color(0xFF102A43),
    secondary = Color(0xFF334E68),
    onSecondary = Color.White,
    background = Color(0xFFF7FAFD),
    onBackground = Color(0xFF17202A),
    surface = Color.White,
    onSurface = Color(0xFF17202A),
    surfaceVariant = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF526579),
    outlineVariant = Color(0xFFB8C5D3),
    error = Color(0xFFB42318),
    onError = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeRouteApp(viewModel: SafeRouteViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state.signedIn) {
        createSafetyNotificationChannel(context)
        if (state.signedIn && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    if (!state.signedIn) { AuthScreen(state.busy, state.message, state.firebaseReady, viewModel::clearMessage, viewModel::login, viewModel::register, viewModel::signInWithGoogleToken); return }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(stringResource(R.string.app_name)); Text(stringResource(R.string.dashboard), style = MaterialTheme.typography.labelMedium) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.busy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp)); Text(stringResource(R.string.working)) }
            state.message?.let { MessageCard(it) { viewModel.clearMessage() } }
            NavBar(selected = screen) { screen = it }
            when (screen) {
                Screen.HOME -> HomeScreen(viewModel::triggerSos) { screen = it }
                Screen.CONTACTS -> ContactsScreen(state.contacts, viewModel::saveEmergencyContact)
                Screen.INCIDENT -> IncidentScreen(viewModel::reportIncident)
                Screen.RESOURCES -> ResourcesScreen(state.resources, state.busy, viewModel::findSafetyResources)
                Screen.SETTINGS -> SettingsScreen(viewModel::saveSettings, viewModel::signOut) { code -> LocaleHelper.save(context, code); (context as? Activity)?.recreate() }
            }
        }
    }
}

@Composable private fun AuthScreen(busy: Boolean, message: String?, firebaseReady: Boolean, clearMessage: () -> Unit, login: (String, String) -> Unit, register: (String, String, String) -> Unit, googleSignIn: (String) -> Unit) {
    val appTypography = MaterialTheme.typography
    MaterialTheme(colorScheme = AuthColorScheme, typography = appTypography) {
        Surface(modifier = Modifier.fillMaxSize(), color = AuthColorScheme.background, contentColor = AuthColorScheme.onBackground) {
            var registerMode by rememberSaveable { mutableStateOf(false) }
            var name by rememberSaveable { mutableStateOf("") }
            var email by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            var googleError by rememberSaveable { mutableStateOf<String?>(null) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            Column(
                Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.auth_intro))
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (!firebaseReady) MessageCard("Firebase is not configured. Add app/google-services.json, then rebuild. See FIREBASE_SETUP.md.") { }
                message?.let { MessageCard(it, clearMessage) }
                Spacer(Modifier.height(16.dp))
                if (registerMode) Input(stringResource(R.string.name), name) { name = it }
                Input(stringResource(R.string.email), email) { email = it }
                Input(stringResource(R.string.password), password, true) { password = it }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { if (registerMode) register(name, email, password) else login(email, password) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(if (registerMode) R.string.create_account else R.string.login)) }
                OutlinedButton(onClick = {
                    googleError = null
                    val webClientId = context.getString(R.string.saferoute_google_web_client_id)
                    if (webClientId.isBlank()) googleError = "Google sign-in setup is incomplete. Add the Web client ID in app/src/main/res/values/strings.xml and enable Google in Firebase."
                    else scope.launch {
                        try {
                            val option = GetGoogleIdOption.Builder().setServerClientId(webClientId).setFilterByAuthorizedAccounts(false).build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                            val activity = context as? Activity ?: error("Could not open Google sign-in.")
                            val response = CredentialManager.create(context).getCredential(activity, request)
                            val credential = response.credential
                            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                googleSignIn(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                            } else googleError = "Google did not return a supported sign-in credential."
                        } catch (e: Exception) { googleError = e.localizedMessage ?: "Google sign-in was cancelled or failed." }
                    }
                }, enabled = !busy && firebaseReady, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.continue_google))
                }
                googleError?.let { MessageCard(it) { googleError = null } }
                OutlinedButton(onClick = { registerMode = !registerMode }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (registerMode) R.string.already_have_account else R.string.create_an_account))
                }
            }
        }
    }
}

@Composable private fun NavBar(selected: Screen, onSelected: (Screen) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavButton(stringResource(R.string.home), selected == Screen.HOME, Modifier.weight(1f)) { onSelected(Screen.HOME) }
            NavButton(stringResource(R.string.contacts), selected == Screen.CONTACTS, Modifier.weight(1f)) { onSelected(Screen.CONTACTS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavButton(stringResource(R.string.report), selected == Screen.INCIDENT, Modifier.weight(1f)) { onSelected(Screen.INCIDENT) }
            NavButton(stringResource(R.string.settings), selected == Screen.SETTINGS, Modifier.weight(1f)) { onSelected(Screen.SETTINGS) }
        }
    }
}

@Composable private fun NavButton(label: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    if (selected) Button(onClick = click, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = click, modifier = modifier) { Text(label) }
}

@Composable private fun HomeScreen(triggerSos: () -> Unit, navigate: (Screen) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Text(stringResource(R.string.dashboard), style = MaterialTheme.typography.headlineSmall)
    InfoCard(stringResource(R.string.safety_status), stringResource(R.string.ready_safety))
    Button(
        onClick = { confirm = true },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.sos), style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.get_help)) } }
    SafetyCheckCard()
    Text(stringResource(R.string.quick_actions), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(stringResource(R.string.add_contact), Modifier.weight(1f)) { navigate(Screen.CONTACTS) }
        QuickAction(stringResource(R.string.report_incident), Modifier.weight(1f)) { navigate(Screen.INCIDENT) }
    }
    QuickAction(stringResource(R.string.nearby_police), Modifier.fillMaxWidth()) { navigate(Screen.RESOURCES) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.activate_sos)) }, text = { Text(stringResource(R.string.sos_explanation)) }, confirmButton = { Button(onClick = { confirm = false; triggerSos() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.activate)) } }, dismissButton = { OutlinedButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SafetyCheckCard() {
    val context = LocalContext.current
    var active by rememberSaveable { mutableStateOf(context.getSharedPreferences("saferoute_preferences", 0).getBoolean("safety_check_active", false)) }
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.safety_check), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (active) R.string.check_active else R.string.start_check))
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            if (active) cancelSafetyCheck(context) else startSafetyCheck(context)
            active = !active
        }) { Text(stringResource(if (active) R.string.end_check else R.string.start_30_check)) }
    } }
}
@Composable private fun QuickAction(label: String, modifier: Modifier, click: () -> Unit) { ElevatedCard(modifier) { OutlinedButton(onClick = click, modifier = Modifier.fillMaxWidth().padding(4.dp)) { Text(label, maxLines = 2) } } }
@Composable private fun InfoCard(title: String, body: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body) } } }
@Composable private fun ContactsScreen(contacts: List<EmergencyContact>, save: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.contacts), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.contact_intro))
    Input(stringResource(R.string.contact_name), name) { name = it }
    Input(stringResource(R.string.phone_number), phone) { phone = it }
    Button(onClick = { save(name, phone) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_contact)) }
    Text(stringResource(R.string.saved_contacts), style = MaterialTheme.typography.titleMedium)
    if (contacts.isEmpty()) {
        Text(stringResource(R.string.no_contacts))
    } else {
        contacts.forEach { contact ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(contact.phone)
                    if (contact.waitingToSync) Text(stringResource(R.string.contact_waiting_to_sync), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
@Composable private fun IncidentScreen(save: (String, String) -> Unit) {
    var category by rememberSaveable { mutableStateOf("") }; var description by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.report_incident), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.incident_intro)); Input(stringResource(R.string.incident_category), category) { category = it }; Input(stringResource(R.string.description), description) { description = it }
    Button(onClick = { save(category, description) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.submit_incident)) }
}
@Composable private fun ResourcesScreen(resources: List<SafetyResource>, busy: Boolean, search: (String) -> Unit) {
    var place by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.find_police), style = MaterialTheme.typography.headlineSmall)
    InfoCard(stringResource(R.string.safety_resources), stringResource(R.string.resource_intro))
    Input(stringResource(R.string.town_suburb), place) { place = it }
    Button(onClick = { search(place) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.find_police))
    }
    resources.forEach { resource ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(resource.display_name, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("Coordinates: ${resource.lat}, ${resource.lon}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable private fun SettingsScreen(save: (String, String, Boolean) -> Unit, signOut: () -> Unit, onLanguageSelected: (String) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var languageCode by rememberSaveable { mutableStateOf(LocaleHelper.selected(context)) }
    var languageMenu by remember { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(true) }
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
    InfoCard(stringResource(R.string.privacy), stringResource(R.string.privacy_intro))
    Input(stringResource(R.string.name), name) { name = it }
    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
    Box {
        OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(when (languageCode) { "af" -> R.string.afrikaans; "xh" -> R.string.isixhosa; else -> R.string.english }))
        }
        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
            listOf("en" to R.string.english, "af" to R.string.afrikaans, "xh" to R.string.isixhosa).forEach { (code, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { languageCode = code; languageMenu = false; onLanguageSelected(code) })
            }
        }
    }
    OutlinedButton(onClick = { sharing = !sharing }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (sharing) R.string.sharing_on else R.string.sharing_off)) }
    Button(onClick = { save(name, languageCode, sharing) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_settings)) }
    OutlinedButton(onClick = signOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout)) }
}
@Composable private fun Input(label: String, value: String, password: Boolean = false, changed: (String) -> Unit) { OutlinedTextField(value = value, onValueChange = changed, label = { Text(label) }, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, modifier = Modifier.fillMaxWidth()) }
@Composable private fun MessageCard(text: String, dismiss: () -> Unit) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f)); OutlinedButton(onClick = dismiss) { Text("OK") } } } }
