package com.opendroid.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.opendroid.ai.ui.theme.AccentCyan
import com.opendroid.ai.ui.theme.AccentNeonGreen
import com.opendroid.ai.ui.theme.BorderColor
import com.opendroid.ai.ui.theme.CardBackground
import com.opendroid.ai.ui.theme.DarkBackground
import com.opendroid.ai.ui.theme.TextPrimary
import com.opendroid.ai.ui.theme.TextSecondary

private const val COMPOSIO_DASHBOARD_URL = "https://dashboard.composio.dev/"

private data class MarketplaceApp(
    val name: String,
    val description: String
)

private enum class ConnectionState {
    DISCONNECTED,
    AUTHENTICATING,
    CONNECTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposioMarketplaceScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var connectionState by rememberSaveable { mutableStateOf(ConnectionState.DISCONNECTED) }
    var accountName by rememberSaveable { mutableStateOf("Not connected") }
    val connectedApps = remember { mutableStateListOf<MarketplaceApp>() }

    val featuredApps = remember {
        listOf(
            MarketplaceApp("GitHub", "Repositories, issues, and pull requests."),
            MarketplaceApp("Gmail", "Read, draft, and send email actions."),
            MarketplaceApp("Slack", "Messages, channels, and workspace actions."),
            MarketplaceApp("Notion", "Pages, databases, and workspace notes.")
        )
    }

    fun syncMarketplace() {
        connectionState = ConnectionState.CONNECTED
        accountName = "Your Composio account"
        connectedApps.clear()
        connectedApps.addAll(featuredApps)
    }

    DisposableEffect(lifecycleOwner, connectionState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && connectionState == ConnectionState.AUTHENTICATING) {
                syncMarketplace()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "COMPOSIO MARKETPLACE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = AccentNeonGreen,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AccentCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = AccentCyan
                                )
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = accountName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = when (connectionState) {
                                        ConnectionState.CONNECTED -> "Synced from your Composio account"
                                        ConnectionState.AUTHENTICATING -> "Finish sign-in in your browser, then come back here"
                                        ConnectionState.DISCONNECTED -> "Sign in to unlock the marketplace"
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (connectionState == ConnectionState.CONNECTED) {
                                Text(
                                    text = "LIVE",
                                    color = AccentNeonGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                connectionState = ConnectionState.AUTHENTICATING
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(COMPOSIO_DASHBOARD_URL))
                                )
                            }
                        ) {
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> "Reconnect Composio"
                                    ConnectionState.AUTHENTICATING -> "Waiting for browser sign-in"
                                    ConnectionState.DISCONNECTED -> "Sign in with Composio"
                                }
                            )
                        }

                        if (connectionState == ConnectionState.CONNECTED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { syncMarketplace() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Sync marketplace")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Composio keeps the user sign-in and connection lifecycle on its side; OpenDroid only refreshes the connected apps list.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            item {
                Text(
                    text = "CONNECTED APPS",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            if (connectedApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Text(
                            text = "No connected apps yet. Sign in, then return to sync your marketplace.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                connectedApps.forEach { app ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardBackground)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = null,
                                    tint = AccentNeonGreen
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = app.description,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = AccentCyan
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "FEATURED INTEGRATIONS",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            featuredApps.forEach { app ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            .clickable {
                                if (connectionState == ConnectionState.CONNECTED && connectedApps.none { it.name == app.name }) {
                                    connectedApps.add(app)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = AccentNeonGreen
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = app.description,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = AccentCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
