package com.linuxkernel44.openwebdav.ui

import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.linuxkernel44.openwebdav.R
import com.linuxkernel44.openwebdav.data.DavError

@Composable
fun OpenWebDavRoot(viewModel: MainViewModel) {
    val session by viewModel.session.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(session) {
        if (session !is Session.Active) showSettings = false
    }

    when (val current = session) {
        Session.Loading -> Box(
            Modifier.fillMaxSize().then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        Session.LoggedOut -> LoginScreen(viewModel)

        is Session.Active -> {
            if (showSettings) {
                BackHandler { showSettings = false }
                SettingsScreen(viewModel, current.credentials, onBack = { showSettings = false })
            } else {
                BrowserScreen(viewModel, current, onOpenSettings = { showSettings = true })
            }
        }
    }
}

fun errorMessage(context: Context, error: DavError): String = when (error) {
    is DavError.Auth -> context.getString(R.string.error_auth)
    is DavError.NotFound -> context.getString(R.string.error_not_found)
    is DavError.Http -> context.getString(R.string.error_server, error.code)
    is DavError.Network -> context.getString(R.string.error_network)
    is DavError.Parse -> context.getString(R.string.error_parse)
}

@Composable
fun errorText(error: DavError): String = errorMessage(LocalContext.current, error)
