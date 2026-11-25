package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.JarvisNeuralEngine
import com.example.core.PreferencesRepository
import com.example.core.SystemTelemetryMonitor
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.signOutUser
import com.example.data.HudMode
import com.example.data.JarvisRepository
import com.example.ui.JarvisViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.JarvisHudScreen
import com.example.ui.theme.JarvisOsTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Full-bleed edge-to-edge dark obsidian styling
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            JarvisAppNavigation()
        }
    }
}

@Composable
fun JarvisAppNavigation() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        JarvisOsTheme(hudMode = HudMode.STANDARD) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        val uid = user.uid
        key(uid) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val credentialManager = remember(context) { CredentialManager.create(context) }

            val viewModel: JarvisViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        JarvisViewModel(
                            appContext = app.applicationContext,
                            currentUserId = uid,
                            repository = JarvisRepository(db, auth),
                            preferencesRepository = PreferencesRepository(app.applicationContext),
                            telemetryMonitor = SystemTelemetryMonitor(app.applicationContext),
                            neuralEngine = JarvisNeuralEngine()
                        )
                    }
                }
            )

            val hudMode by viewModel.hudMode.collectAsStateWithLifecycle()
            JarvisOsTheme(hudMode = hudMode) {
                JarvisHudScreen(
                    viewModel = viewModel,
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    }
                )
            }
        }
    }
}
