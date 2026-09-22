package com.example.opsc6312_poe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.opsc6312_poe.ui.theme.OPSC6312POETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OPSC6312POETheme { SafeRouteApp() } }
    }
}

private enum class Screen { HOME, CONTACTS, INCIDENT, RESOURCES, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeRouteApp(viewModel: SafeRouteViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    if (!state.signedIn) { AuthScreen(state.busy, state.message, state.firebaseReady, viewModel::login, viewModel::register); return }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("SafeRoute SA"); Text("Personal safety dashboard", style = MaterialTheme.typography.labelMedium) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.busy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp)); Text("Working…") }
            state.message?.let { MessageCard(it) { viewModel.clearMessage() } }
            NavBar(selected = screen) { screen = it }
            when (screen) {
                Screen.HOME -> HomeScreen(viewModel::triggerSos) { screen = it }
                Screen.CONTACTS -> ContactsScreen(viewModel::saveEmergencyContact)
                Screen.INCIDENT -> IncidentScreen(viewModel::reportIncident)
                Screen.RESOURCES -> ResourcesScreen(state.resources, viewModel::findSafetyResources)
                Screen.SETTINGS -> SettingsScreen(viewModel::saveSettings, viewModel::signOut)
            }
        }
    }
}

@Composable private fun AuthScreen(busy: Boolean, message: String?, firebaseReady: Boolean, login: (String, String) -> Unit, register: (String, String, String) -> Unit) {
    var registerMode by rememberSaveable { mutableStateOf(false) }; var name by rememberSaveable { mutableStateOf("") }; var email by rememberSaveable { mutableStateOf("") }; var password by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("SafeRoute SA", style = MaterialTheme.typography.headlineLarge)
                Text("Your safety. Your control.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Register securely, keep emergency contacts close, and find local safety resources.")
            }
        }
        Spacer(Modifier.height(20.dp))
        if (!firebaseReady) MessageCard("Firebase is not configured. Add app/google-services.json, then rebuild. See FIREBASE_SETUP.md.") {}
        message?.let { MessageCard(it) {} }; Spacer(Modifier.height(16.dp))
        if (registerMode) Input("Full name", name) { name = it }
        Input("Email", email) { email = it }; Input("Password (minimum 6 characters)", password, true) { password = it }
        Spacer(Modifier.height(8.dp)); Button(onClick = { if (registerMode) register(name, email, password) else login(email, password) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (registerMode) "Create secure account" else "Log in securely") }
        OutlinedButton(onClick = { registerMode = !registerMode }, modifier = Modifier.fillMaxWidth()) { Text(if (registerMode) "I already have an account" else "Create an account") }
    }
}

@Composable private fun NavBar(selected: Screen, onSelected: (Screen) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavButton("Home", selected == Screen.HOME, Modifier.weight(1f)) { onSelected(Screen.HOME) }
            NavButton("Contacts", selected == Screen.CONTACTS, Modifier.weight(1f)) { onSelected(Screen.CONTACTS) }
            NavButton("Report", selected == Screen.INCIDENT, Modifier.weight(1f)) { onSelected(Screen.INCIDENT) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavButton("Find help", selected == Screen.RESOURCES, Modifier.weight(1f)) { onSelected(Screen.RESOURCES) }
            NavButton("Settings", selected == Screen.SETTINGS, Modifier.weight(1f)) { onSelected(Screen.SETTINGS) }
        }
    }
}

@Composable private fun NavButton(label: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    if (selected) Button(onClick = click, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = click, modifier = modifier) { Text(label) }
}

@Composable private fun HomeScreen(triggerSos: () -> Unit, navigate: (Screen) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Text("Safety dashboard", style = MaterialTheme.typography.headlineSmall)
    InfoCard("Safety status", "Ready when you need it. Add trusted contacts before an emergency.")
    Button(
        onClick = { confirm = true },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("SOS", style = MaterialTheme.typography.headlineMedium); Text("Get help now") } }
    SafetyCheckCard()
    Text("Quick actions", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction("Add contact", Modifier.weight(1f)) { navigate(Screen.CONTACTS) }
        QuickAction("Report incident", Modifier.weight(1f)) { navigate(Screen.INCIDENT) }
    }
    QuickAction("Find nearby police stations", Modifier.fillMaxWidth()) { navigate(Screen.RESOURCES) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Activate SOS?") }, text = { Text("This records an active emergency event in your SafeRoute account. Confirm only when you need help.") }, confirmButton = { Button(onClick = { confirm = false; triggerSos() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Activate SOS") } }, dismissButton = { OutlinedButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable private fun SafetyCheckCard() { var active by rememberSaveable { mutableStateOf(false) }; ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Safety Check", style = MaterialTheme.typography.titleMedium); Text(if (active) "A 30-minute check-in is active. Remember to confirm that you are safe." else "Start a timed check-in when travelling or meeting someone."); Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { active = !active }) { Text(if (active) "End Safety Check" else "Start 30-minute Safety Check") } } } }
@Composable private fun QuickAction(label: String, modifier: Modifier, click: () -> Unit) { ElevatedCard(modifier) { OutlinedButton(onClick = click, modifier = Modifier.fillMaxWidth().padding(4.dp)) { Text(label, maxLines = 2) } } }
@Composable private fun InfoCard(title: String, body: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body) } } }
@Composable private fun ContactsScreen(save: (String, String) -> Unit) { var name by rememberSaveable { mutableStateOf("") }; var phone by rememberSaveable { mutableStateOf("") }; Text("Emergency contacts", style = MaterialTheme.typography.headlineSmall); Text("Save a trusted person who should be contacted in an emergency."); Input("Contact name", name) { name = it }; Input("Phone number", phone) { phone = it }; Button(onClick = { save(name, phone) }, modifier = Modifier.fillMaxWidth()) { Text("Save emergency contact") } }
@Composable private fun IncidentScreen(save: (String, String) -> Unit) { var category by rememberSaveable { mutableStateOf("") }; var description by rememberSaveable { mutableStateOf("") }; Text("Report an incident", style = MaterialTheme.typography.headlineSmall); Text("Share clear details to help build community safety awareness."); Input("Category (for example, suspicious activity)", category) { category = it }; Input("Description", description) { description = it }; Button(onClick = { save(category, description) }, modifier = Modifier.fillMaxWidth()) { Text("Submit incident report") } }
@Composable private fun ResourcesScreen(resources: List<SafetyResource>, search: (String) -> Unit) { var place by rememberSaveable { mutableStateOf("") }; Text("Find help near you", style = MaterialTheme.typography.headlineSmall); InfoCard("Safety resources", "Search for police stations in a South African town or suburb using a public REST API."); Input("Town or suburb", place) { place = it }; Button(onClick = { search(place) }, modifier = Modifier.fillMaxWidth()) { Text("Find police stations") }; resources.forEach { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(it.display_name, style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(4.dp)); Text("Coordinates: ${it.lat}, ${it.lon}", style = MaterialTheme.typography.bodySmall) } } } }
@Composable private fun SettingsScreen(save: (String, String, Boolean) -> Unit, signOut: () -> Unit) { var name by rememberSaveable { mutableStateOf("") }; var language by rememberSaveable { mutableStateOf("English") }; var sharing by rememberSaveable { mutableStateOf(true) }; Text("Settings", style = MaterialTheme.typography.headlineSmall); InfoCard("Privacy", "Location sharing is an optional preference. You can switch it off at any time."); Input("Full name", name) { name = it }; Input("Preferred language", language) { language = it }; OutlinedButton(onClick = { sharing = !sharing }, modifier = Modifier.fillMaxWidth()) { Text("Optional location sharing: ${if (sharing) "On" else "Off"}") }; Button(onClick = { save(name, language, sharing) }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }; OutlinedButton(onClick = signOut, modifier = Modifier.fillMaxWidth()) { Text("Log out") } }
@Composable private fun Input(label: String, value: String, password: Boolean = false, changed: (String) -> Unit) { OutlinedTextField(value = value, onValueChange = changed, label = { Text(label) }, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, modifier = Modifier.fillMaxWidth()) }
@Composable private fun MessageCard(text: String, dismiss: () -> Unit) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f)); OutlinedButton(onClick = dismiss) { Text("OK") } } } }
