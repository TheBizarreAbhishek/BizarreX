package com.BizarreX.study.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun SettingsScreen(
    currentUser: FirebaseUser,
    onSignOut: () -> Unit,
    isDarkTheme: Boolean = false,
    onThemeToggle: (Boolean) -> Unit = {}
) {
    val displayName = currentUser.displayName ?: "Student"
    val email = currentUser.email ?: ""
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "S"

    val context = androidx.compose.ui.platform.LocalContext.current
    val photoUrl = remember(currentUser, currentUser.photoUrl) {
        val raw = currentUser.photoUrl?.toString()
        if (raw?.startsWith("tg://") == true) raw.removePrefix("tg://") else raw
    }
    
    var activeRemoteUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(photoUrl) {
        if (photoUrl != null) {
            if (currentUser.photoUrl?.toString()?.startsWith("tg://") == true) {
                val dl = com.BizarreX.study.utils.TelegramStorageHelper.getDirectMediaUrl(context, photoUrl)
                if (dl != null) activeRemoteUrl = dl
            } else {
                activeRemoteUrl = photoUrl
            }
        }
    }

    var showSignOutDialog by remember { mutableStateOf(false) }

    // Sign-out confirmation dialog
    if (showSignOutDialog) {
        OverlayDialog(
            show = true,
            title = "Sign out?",
            summary = "You'll need to sign in again with your Google account.",
            onDismissRequest = { showSignOutDialog = false }
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    text = "Cancel",
                    onClick = { showSignOutDialog = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(16.dp))
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError
                    )
                ) {
                    Text("Sign out")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Settings",
            style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold),
            color = MiuixTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Profile card — real user data
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer),
            pressFeedbackType = PressFeedbackType.None
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeRemoteUrl != null) {
                        coil.compose.AsyncImage(
                            model = activeRemoteUrl,
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = initial,
                            style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = displayName,
                        style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = email,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance section
        SettingsSectionLabel("Appearance")
        SettingsGroup {
            SettingsToggleItem(
                icon = Icons.Rounded.DarkMode,
                title = "Dark Mode",
                subtitle = if (isDarkTheme) "Dark theme active" else "Light theme active",
                checked = isDarkTheme,
                onCheckedChange = { onThemeToggle(it) }
            )
            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
            var dynamicColor by remember { mutableStateOf(true) }
            SettingsToggleItem(
                icon = Icons.Rounded.Palette,
                title = "Dynamic Color",
                subtitle = "Use wallpaper colors (Android 12+)",
                checked = dynamicColor,
                onCheckedChange = { dynamicColor = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications section
        SettingsSectionLabel("Notifications")
        SettingsGroup {
            var notifs by remember { mutableStateOf(true) }
            SettingsToggleItem(
                icon = Icons.Rounded.Notifications,
                title = "Push Notifications",
                subtitle = "Get updates and reminders",
                checked = notifs,
                onCheckedChange = { notifs = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About section
        SettingsSectionLabel("About")
        SettingsGroup {
            SettingsClickItem(
                icon = Icons.Rounded.Info,
                title = "App Version",
                subtitle = "BizarreX v${com.BizarreX.study.BuildConfig.VERSION_NAME}"
            )
            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
            SettingsClickItem(
                icon = Icons.Rounded.Policy,
                title = "Privacy Policy",
                subtitle = "Read our privacy policy"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign out
        SettingsSectionLabel("Account")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.errorContainer),
            onClick = { showSignOutDialog = true },
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Sign Out",
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Signed in as $email",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.None
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
            Text(subtitle, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
            Text(subtitle, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
    }
}
