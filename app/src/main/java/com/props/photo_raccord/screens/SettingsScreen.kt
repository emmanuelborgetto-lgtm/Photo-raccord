package com.props.photo_raccord.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.props.photo_raccord.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(currentTheme: String, onThemeChanged: (String) -> Unit, onProjetRenamed: (String, String) -> Unit, onProjetDeleted: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photo_raccord_prefs", Context.MODE_PRIVATE) }
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val scope = rememberCoroutineScope()
    var customTreeUri by remember { mutableStateOf(prefs.getString("storage_tree_uri", null)) }
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                customTreeUri = selectedUri.toString()
                prefs.edit { putString("storage_tree_uri", selectedUri.toString()) }
            } catch (e: Exception) { Log.e("Settings", "Permission error", e) }
        }
    }
    fun getDisplayFolderPath(uriString: String?): String = uriString?.toUri()?.path?.substringAfterLast(":") ?: "DCIM (Par défaut)"
    val projets by photoDao.getDistinctProjets().collectAsState(initial = emptyList())
    var projectToRename by remember { mutableStateOf<String?>(null) }
    var newProjectName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var isCleaning by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Paramètres", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onClose) { Text("Retour") }
        }
        HorizontalDivider()
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Palette de couleurs", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeButton("AMBER", currentTheme, onThemeChanged, Modifier.weight(1f))
                    ThemeButton("VIOLET", currentTheme, onThemeChanged, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeButton("TURQUOISE", currentTheme, onThemeChanged, Modifier.weight(1f))
                    ThemeButton("SYSTEM", currentTheme, onThemeChanged, Modifier.weight(1f))
                }
            }
            HorizontalDivider()
            Text("Dossier de stockage", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dossier actuel : ${getDisplayFolderPath(customTreeUri)}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.weight(1f)) { Text("Changer le dossier") }
                        if (!customTreeUri.isNullOrEmpty()) IconButton(onClick = { customTreeUri = null; prefs.edit { remove("storage_tree_uri") } }) { Icon(Icons.Default.Close, "Réinitialiser") }
                    }
                }
            }
            HorizontalDivider()
            Text("Projets enregistrés", style = MaterialTheme.typography.titleMedium)
            if (projets.isEmpty()) Text("Aucun projet enregistré.", style = MaterialTheme.typography.bodyMedium)
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { projets.forEach { proj -> ProjectCard(proj, { old, new -> projectToRename = old; newProjectName = new }, { projectToDelete = it }) } }
            HorizontalDivider()
            Text("Maintenance", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                isCleaning = true
                scope.launch(Dispatchers.IO) {
                    val allPhotos = photoDao.getAllPhotosOnce()
                    val orphans = allPhotos.filter { photo ->
                        try {
                            val uri = photo.uri.toUri()
                            val exists = context.contentResolver.openInputStream(uri)?.use { true } ?: false
                            !exists
                        } catch (_: Exception) { true }
                    }
                    if (orphans.isNotEmpty()) photoDao.deletePhotos(orphans)
                    withContext(Dispatchers.Main) { isCleaning = false; Toast.makeText(context, "${orphans.size} référence(s) orpheline(s) supprimée(s)", Toast.LENGTH_SHORT).show() }
                }
            }, enabled = !isCleaning, modifier = Modifier.fillMaxWidth()) { Text(if (isCleaning) "Nettoyage en cours..." else "Nettoyer les références supprimées") }
        }
    }

    projectToRename?.let { oldName ->
        AlertDialog(onDismissRequest = { projectToRename = null }, title = { Text("Renommer le projet") }, text = {
            OutlinedTextField(value = newProjectName, onValueChange = { newProjectName = it }, label = { Text("Nouveau nom") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { if (newProjectName.isNotBlank() && newProjectName != oldName) scope.launch { photoDao.renameProjet(oldName, newProjectName); onProjetRenamed(oldName, newProjectName); projectToRename = null } }, enabled = newProjectName.isNotBlank() && newProjectName != oldName) { Text("Valider") } },
            dismissButton = { TextButton(onClick = { projectToRename = null }) { Text("Annuler") } })
    }

    projectToDelete?.let { proj ->
        AlertDialog(onDismissRequest = { projectToDelete = null }, title = { Text("Supprimer le projet ?") }, text = { Text("Toutes les références des photos de \"$proj\" seront supprimées.") },
            confirmButton = { TextButton(onClick = { scope.launch { photoDao.deleteProjet(proj); onProjetDeleted(proj); projectToDelete = null } }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { projectToDelete = null }) { Text("Annuler") } })
    }
}

@Composable private fun ThemeButton(theme: String, currentTheme: String, onThemeChanged: (String) -> Unit, modifier: Modifier) {
    Button(onClick = { onThemeChanged(theme) }, modifier = modifier, colors = ButtonDefaults.buttonColors(
        containerColor = if (currentTheme == theme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (currentTheme == theme) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    )) { Text(when (theme) { "AMBER" -> "Ambre"; "VIOLET" -> "Violet"; "TURQUOISE" -> "Turquoise"; else -> "Système" }) }
}

@Composable private fun ProjectCard(project: String, onRename: (String, String) -> Unit, onDelete: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(project, style = MaterialTheme.typography.bodyLarge)
            Row {
                IconButton(onClick = { onRename(project, project) }) { Icon(Icons.Default.Edit, "Renommer") }
                IconButton(onClick = { onDelete(project) }) { Icon(Icons.Default.Delete, "Supprimer") }
            }
        }
    }
}
