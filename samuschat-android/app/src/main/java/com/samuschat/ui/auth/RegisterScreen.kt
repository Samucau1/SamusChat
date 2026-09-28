package com.samuschat.ui.auth
import androidx.compose.runtime.Composable

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onGoToLogin: () -> Unit) = AuthForm(true, viewModel, onGoToLogin)
