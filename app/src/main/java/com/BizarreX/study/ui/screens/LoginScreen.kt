package com.BizarreX.study.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BizarreX.study.AuthState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.CircularProgressIndicator

@Composable
fun LoginScreen(
    authState: AuthState,
    onSignInClick: () -> Unit
) {
    val isLoading = authState is AuthState.Loading
    val errorMessage = (authState as? AuthState.Error)?.message

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0A),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {

            // App icon
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically { -it } + fadeIn()
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6750A4), Color(0xFF9C27B0))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Brand name
            Text(
                text = "BizarreX",
                style = MiuixTheme.textStyles.title1.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your ultimate AKTU study companion.\nLearn together, grow together.",
                style = MiuixTheme.textStyles.body1.copy(
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Google Sign-In button
            Button(
                onClick = onSignInClick,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                cornerRadius = 14.dp,
                colors = ButtonDefaults.buttonColors(
                    color = Color.White,
                    contentColor = Color(0xFF1F1F1F),
                    disabledColor = Color.White.copy(alpha = 0.6f),
                    disabledContentColor = Color(0xFF1F1F1F).copy(alpha = 0.6f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFF6750A4),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    // Google "G" logo
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            style = MiuixTheme.textStyles.title3.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF4285F4)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        style = MiuixTheme.textStyles.button.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.errorContainer),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MiuixTheme.textStyles.footnote1.copy(
                            color = MiuixTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "By continuing, you agree to our\nTerms of Service and Privacy Policy",
                style = MiuixTheme.textStyles.footnote2.copy(
                    color = Color.White.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
