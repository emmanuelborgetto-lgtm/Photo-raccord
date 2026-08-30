package com.props.photo_raccord.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.props.photo_raccord.AppDatabase
import com.props.photo_raccord.BarlowCondensed
import com.props.photo_raccord.DM_Mono
import com.props.photo_raccord.ProjectEntity
import kotlinx.coroutines.launch

@SuppressLint("LocalContextResourcesRead", "LocalContextGetResourceValueCall", "DefaultLocale", "DiscouragedApi")
@Composable
fun ProjectSelectionScreen(
    onProjectSelected: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val scope = rememberCoroutineScope()
    val projetsExistants by photoDao.getDistinctProjets().collectAsState(initial = emptyList())
    val photoCounts by photoDao.getPhotoCountsByProjet().collectAsState(initial = emptyList())
    val countsByProjet = remember(photoCounts) { photoCounts.associate { it.projet to it.count } }

    var inputProjet by remember { mutableStateOf("") }
    val versionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "0.1"
        } catch (_: Exception) { "0.1" }
    }
    val buildDate = remember(context) {
        val resId = context.resources.getIdentifier("git_commit_date", "string", context.packageName)
        if (resId != 0) context.getString(resId) else "2026"
    }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = buildAnnotatedString {
                    append("PHOTO")
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("•") }
                    append("RACCORD")
                }, fontSize = 32.sp, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "Continuité & raccords visuels", style = MaterialTheme.typography.bodyMedium, fontFamily = BarlowCondensed, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenSettings) { Icon(imageVector = Icons.Default.Settings, contentDescription = "Paramètres", tint = MaterialTheme.colorScheme.onSurface) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = inputProjet,
                onValueChange = { inputProjet = it },
                placeholder = { Text("Nouveau projet", fontFamily = DM_Mono) },
                leadingIcon = { Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (inputProjet.isNotEmpty()) IconButton(onClick = { inputProjet = "" }) { Icon(Icons.Default.Close, contentDescription = "Effacer") }
                        TextButton(
                            onClick = {
                                val nom = inputProjet.trim()
                                if (nom.isNotEmpty()) {
                                    scope.launch {
                                        photoDao.createProjet(ProjectEntity(nom = nom))
                                        inputProjet = ""
                                        onProjectSelected(nom)
                                    }
                                }
                            },
                            enabled = inputProjet.isNotBlank()
                        ) { Text(text = "+ CRÉER", fontFamily = DM_Mono, fontWeight = FontWeight.Bold) }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(text = "${projetsExistants.size} PROJETS", style = MaterialTheme.typography.labelSmall, fontFamily = DM_Mono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(projetsExistants, key = { _, item -> item }) { index, item ->
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cardBackgroundColor = if (isPressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                val photoCount = countsByProjet[item] ?: 0
                Card(onClick = { onProjectSelected(item) }, interactionSource = interactionSource, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBackgroundColor), shape = MaterialTheme.shapes.medium) {
                    Row(modifier = Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(52.dp).fillMaxHeight().background(if (isPressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                            Text(text = String.format("%02d", index + 1), style = MaterialTheme.typography.bodyMedium, fontFamily = DM_Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = item, style = MaterialTheme.typography.titleMedium, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = if (photoCount <= 1) "$photoCount photo" else "$photoCount photos", style = MaterialTheme.typography.bodySmall, fontFamily = DM_Mono, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 8.dp))
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sélectionner", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Photo-raccord v$versionName - $buildDate", style = MaterialTheme.typography.bodySmall, fontFamily = DM_Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = " • ", style = MaterialTheme.typography.bodySmall, fontFamily = DM_Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "À propos", style = MaterialTheme.typography.bodySmall, fontFamily = DM_Mono, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { showAboutDialog = true })
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(text = "À PROPOS", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Photo-raccord est une application dédiée à la prise de vue et au suivi des raccords sur les tournages cinématographiques et audiovisuels.\n\nElle permet d'organiser vos photographies par projet, séquence et décor.", style = MaterialTheme.typography.bodyMedium, fontFamily = DM_Mono)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Contact : ", style = MaterialTheme.typography.bodyMedium, fontFamily = DM_Mono)
                        Text(text = "Emmanuel Borgetto", style = MaterialTheme.typography.bodyMedium, fontFamily = DM_Mono, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply { data = ("mailto:emmanuel.borgetto@gmail.com?subject=" + Uri.encode("Photo-Raccord")).toUri() }
                            try { context.startActivity(intent) } catch (_: Exception) { }
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("FERMER", fontFamily = DM_Mono) } }
        )
    }
}
