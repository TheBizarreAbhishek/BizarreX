package com.BizarreX.study

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BizarreX.study.ui.screens.CommunityScreen
import com.BizarreX.study.ui.screens.LoginScreen
import com.BizarreX.study.ui.screens.PlaceholderScreen
import com.BizarreX.study.ui.screens.SettingsScreen
import com.BizarreX.study.ui.screens.StudyScreen
import com.BizarreX.study.ui.theme.BizarreXTheme
import com.BizarreX.study.utils.UpdateChecker
import com.BizarreX.study.utils.UpdateInfo
import com.BizarreX.study.utils.VideoDownloadManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Initialize download vault state from disk
        VideoDownloadManager.init(this)
        setContent {
            BizarreXTheme {
                RootApp()
            }
        }
    }
}

@Composable
fun RootApp() {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    // Check for update in background (skip in debug builds)
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) {
            updateInfo = UpdateChecker.checkForUpdate()
        }
    }

    // Force-update dialog — non-dismissible
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { /* non-dismissible */ },
            title = { Text("Update Available 🚀", fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("BizarreX ${info.latestVersion} is available (you have ${info.currentVersion}).")
                    if (info.releaseNotes.isNotBlank()) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                        Text(info.releaseNotes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(info.apkDownloadUrl))
                    context.startActivity(intent)
                }) { Text("Update Now") }
            },
            dismissButton = null
        )
    }

    when (authState) {
        is AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize())
        }
        is AuthState.Authenticated -> {
            BizarreXApp(
                authViewModel = authViewModel,
                currentUser = (authState as AuthState.Authenticated).user
            )
        }
        is AuthState.Unauthenticated, is AuthState.Error -> {
            LoginScreen(
                authState = authState,
                onSignInClick = { authViewModel.signInWithGoogle(context) }
            )
        }
    }
}

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val navItems = listOf(
    NavItem("Study",     Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    NavItem("Community", Icons.Filled.Groups,      Icons.Outlined.Groups),
    NavItem("Explore",   Icons.Filled.Explore,     Icons.Outlined.Explore),
    NavItem("Settings",  Icons.Filled.Settings,    Icons.Outlined.Settings)
)

@Composable
fun BizarreXApp(
    authViewModel: AuthViewModel,
    currentUser: com.google.firebase.auth.FirebaseUser
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    navItems.forEachIndexed { index, item ->
                        val selected = selectedTab == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "nav_transition"
            ) { tab ->
                when (tab) {
                    0 -> StudyScreen(
                        onFullscreenVisibilityChanged = { isVisible ->
                            isBottomBarVisible = !isVisible
                        }
                    )
                    1 -> CommunityScreen(
                        currentUser = currentUser,
                        onChatVisibilityChanged = { isVisible ->
                            isBottomBarVisible = !isVisible
                        }
                    )
                    2 -> PlaceholderScreen(
                        emoji = "🔭",
                        title = "Explore",
                        subtitle = "Discover trending topics, exams, and study groups near you!"
                    )
                    3 -> SettingsScreen(
                        currentUser = currentUser,
                        onSignOut = { authViewModel.signOut() }
                    )
                }
            }
        }
    }
}