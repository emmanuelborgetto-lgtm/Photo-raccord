/*
 * Photoraccord
 * Copyright (C) 2026 Emmanuel Borgetto
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.props.photo_raccord.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
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
    var showInGallery by remember { mutableStateOf(prefs.getBoolean("show_in_gallery", true)) }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                customTreeUri = selectedUri.toString()
                prefs.edit { putString("storage_tree_uri", selectedUri.toString()) }

                scope.launch(Dispatchers.IO) {
                    toggleNomediaFile(context, selectedUri.toString(), showInGallery)
                }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PARAMÈTRES",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge
            )
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Afficher les photos dans la galerie", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = showInGallery,
                            onCheckedChange = { checked ->
                                showInGallery = checked
                                prefs.edit { putBoolean("show_in_gallery", checked) }
                                scope.launch(Dispatchers.IO) {
                                    toggleNomediaFile(context, customTreeUri, checked)
                                }
                            }
                        )
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

private fun toggleNomediaFile(context: Context, treeUriString: String?, showInGallery: Boolean) {
    if (treeUriString != null) {
        // Logique existante pour un dossier personnalisé (SAF)
        try {
            val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
            if (rootDir != null && rootDir.exists()) {
                val nomediaFile = rootDir.findFile(".nomedia")
                if (showInGallery) {
                    nomediaFile?.delete()
                } else {
                    if (nomediaFile == null) {
                        rootDir.createFile("*/*", ".nomedia")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Settings", "Erreur lors de la modification de .nomedia (SAF)", e)
        }
    } else {
        // Nouvelle logique pour le dossier par défaut (DCIM/PhotoRaccord)
        try {
            val dcimDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
            val photoRaccordDir = java.io.File(dcimDir, "PhotoRaccord")

            // Si le dossier n'existe pas, on le crée uniquement si on veut y cacher des choses
            if (!photoRaccordDir.exists()) {
                if (!showInGallery) photoRaccordDir.mkdirs()
                else return // Rien à faire si on veut afficher et que le dossier n'existe pas
            }

            val nomediaFile = java.io.File(photoRaccordDir, ".nomedia")

            if (showInGallery) {
                if (nomediaFile.exists()) nomediaFile.delete()
            } else {
                if (!nomediaFile.exists()) nomediaFile.createNewFile()
            }
        } catch (e: Exception) {
            Log.e("Settings", "Erreur lors de la modification de .nomedia (Défaut)", e)
        }
    }
}