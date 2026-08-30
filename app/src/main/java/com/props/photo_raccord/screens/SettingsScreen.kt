/* Photoraccord - Settings */
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.props.photo_raccord.utils.ensureDefaultNomedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "photo_raccord_prefs"
private const val PREF_STORAGE_TREE_URI = "storage_tree_uri"
private const val PREF_SHOW_IN_GALLERY = "show_in_gallery"

@Composable
fun SettingsScreen(currentTheme: String, onThemeChanged: (String) -> Unit, onProjetRenamed: (String, String) -> Unit, onProjetDeleted: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val scope = rememberCoroutineScope()
    var customTreeUri by remember { mutableStateOf(prefs.getString(PREF_STORAGE_TREE_URI, null)) }
    var showInGallery by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_IN_GALLERY, true)) }
    var showDcimWarning by remember { mutableStateOf(false) }
    var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { selectedUri ->
            Log.d("Settings", "Dossier sélectionné : $selectedUri")
            Log.d("Settings", "Document ID : ${getTreeDocumentIdSafe(selectedUri)}")
            if (isDcimTreeUri(selectedUri)) {
                pendingFolderUri = selectedUri
                showDcimWarning = true
            } else {
                applySelectedFolderAsync(context, prefs, selectedUri, showInGallery, scope) { customTreeUri = it }
            }
        }
    }

    fun resetToDefault() {
        customTreeUri = null
        prefs.edit { remove(PREF_STORAGE_TREE_URI) }
        scope.launch(Dispatchers.IO) { ensureDefaultNomedia(context, showInGallery) }
    }

    val projets by photoDao.getDistinctProjets().collectAsState(initial = emptyList())
    var projectToRename by remember { mutableStateOf<String?>(null) }
    var newProjectName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var isCleaning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Spacer(Modifier.width(8.dp))
            Text("PARAMÈTRES", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Palette de couleurs", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeButton("AMBER", currentTheme, onThemeChanged, Modifier.weight(1f))
                    ThemeButton("VIOLET", currentTheme, onThemeChanged, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeButton("TURQUOISE", currentTheme, onThemeChanged, Modifier.weight(1f))
                    ThemeButton("SYSTEM", currentTheme, onThemeChanged, Modifier.weight(1f))
                }
            }
            item {
                HorizontalDivider()
                Text("Dossier de stockage", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val displayFolder = customTreeUri?.let { getDisplayFolderPath(it) } ?: "PhotoRaccord (stockage privé)"
                        Text("Dossier actuel : $displayFolder", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { folderPickerLauncher.launch(null) }, Modifier.weight(1f)) { Text("Changer le dossier") }
                            if (!customTreeUri.isNullOrEmpty()) IconButton(onClick = { resetToDefault() }) { Icon(Icons.Default.Close, "Réinitialiser") }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Afficher les photos dans la galerie")
                            Switch(checked = showInGallery, onCheckedChange = { checked ->
                                showInGallery = checked
                                prefs.edit { putBoolean(PREF_SHOW_IN_GALLERY, checked) }
                                scope.launch(Dispatchers.IO) {
                                    if (customTreeUri == null) ensureDefaultNomedia(context, checked) else toggleNomediaFile(context, customTreeUri, checked)
                                }
                            })
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("Projets enregistrés", style = MaterialTheme.typography.titleMedium)
                if (projets.isEmpty()) {
                    Spacer(Modifier.height(8.dp)); Text("Aucun projet enregistré.")
                }
            }
            items(projets, key = { it }) { proj ->
                ProjectCard(proj, { old, new -> projectToRename = old; newProjectName = new }, { projectToDelete = it })
            }
            item {
                HorizontalDivider()
                Text("Maintenance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    isCleaning = true
                    scope.launch(Dispatchers.IO) {
                        val allPhotos = photoDao.getAllPhotosOnce()
                        val orphans = allPhotos.filter { photo ->
                            try { context.contentResolver.openInputStream(photo.uri.toUri())?.use { true } ?: false; false }
                            catch (_: Exception) { true }
                        }
                        if (orphans.isNotEmpty()) photoDao.deletePhotos(orphans)
                        withContext(Dispatchers.Main) {
                            isCleaning = false
                            Toast.makeText(context, "${orphans.size} référence(s) orpheline(s) supprimée(s)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, enabled = !isCleaning, Modifier.fillMaxWidth()) {
                    Text(if (isCleaning) "Nettoyage en cours..." else "Nettoyer les références supprimées")
                }
            }
        }
    }

    projectToRename?.let { oldName ->
        AlertDialog(onDismissRequest = { projectToRename = null }, title = { Text("Renommer le projet") }, text = { OutlinedTextField(value = newProjectName, onValueChange = { newProjectName = it }, label = { Text("Nouveau nom") }, singleLine = true), }, confirmButton = { TextButton(onClick = { if (newProjectName.isNotBlank() && newProjectName != oldName) scope.launch { photoDao.renameProjet(oldName, newProjectName); onProjetRenamed(oldName, newProjectName); projectToRename = null } }) { Text("Valider") } }, dismissButton = { TextButton(onClick = { projectToRename = null }) { Text("Annuler") } })
    }
    projectToDelete?.let { proj ->
        AlertDialog(onDismissRequest = { projectToDelete = null }, title = { Text("Supprimer le projet ?") }, text = { Text("Toutes les références des photos de \"$proj\" seront supprimées.") }, confirmButton = { TextButton(onClick = { scope.launch { photoDao.deleteProjet(proj); onProjetDeleted(proj); projectToDelete = null } }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { projectToDelete = null }) { Text("Annuler") } })
    }
    if (showDcimWarning) {
        AlertDialog(
            onDismissRequest = { showDcimWarning = false; pendingFolderUri = null },
            title = { Text("Attention : dossier DCIM") },
            text = { Text("Vous avez sélectionné le dossier DCIM. Le masquage des photos dans la galerie ne fonctionnera pas de manière fiable dans ce dossier. Pour masquer efficacement les photos, choisissez plutôt le dossier PhotoRaccord proposé par défaut.") },
            confirmButton = { TextButton(onClick = {
                pendingFolderUri?.let { applySelectedFolderAsync(context, prefs, it, showInGallery, scope) { customTreeUri = it } }
                showDcimWarning = false; pendingFolderUri = null
            }) { Text("Utiliser quand même") } },
            dismissButton = { TextButton(onClick = { showDcimWarning = false; pendingFolderUri = null }) { Text("Annuler") } }
        )
    }
}

/** Retourne l'identifiant réel du dossier sélectionné par le Storage Access Framework. */
private fun getTreeDocumentIdSafe(uri: Uri): String? = try { DocumentsContract.getTreeDocumentId(uri) } catch (e: Exception) { Log.w("Settings", "Impossible de lire le documentId de $uri", e); null }

/** Détecte DCIM aussi bien sur le stockage interne que sur une carte SD. */
private fun isDcimTreeUri(uri: Uri): Boolean {
    val documentId = getTreeDocumentIdSafe(uri) ?: return false
    val relativePath = documentId.substringAfter(':', documentId).trim('/')
    val segments = relativePath.split('/').filter { it.isNotEmpty() }
    val result = segments.any { it.equals("DCIM", ignoreCase = true) }
    Log.d("Settings", "Détection DCIM : documentId=$documentId, relativePath=$relativePath, result=$result")
    return result
}

/** Toutes les opérations SAF et filesystem sont exécutées hors du thread UI. */
private fun applySelectedFolderAsync(context: Context, prefs: android.content.SharedPreferences, selectedUri: Uri, showInGallery: Boolean, scope: kotlinx.coroutines.CoroutineScope, onApplied: (String) -> Unit) {
    scope.launch(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.edit { putString(PREF_STORAGE_TREE_URI, selectedUri.toString()) }
            toggleNomediaFile(context, selectedUri.toString(), showInGallery)
            withContext(Dispatchers.Main) { onApplied(selectedUri.toString()) }
        } catch (e: Exception) { Log.e("Settings", "Permission error", e) }
    }
}

private fun getDisplayFolderPath(uriString: String): String = uriString.toUri().path?.substringAfterLast(":") ?: "Dossier personnalisé"

@Composable private fun ThemeButton(theme: String, currentTheme: String, onThemeChanged: (String) -> Unit, modifier: Modifier) { Button(onClick = { onThemeChanged(theme) }, modifier = modifier) { Text(when(theme) { "AMBER" -> "Ambre"; "VIOLET" -> "Violet"; "TURQUOISE" -> "Turquoise"; else -> "Système" }) } }

@Composable private fun ProjectCard(project: String, onRename: (String, String) -> Unit, onDelete: (String) -> Unit) { Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(project); Row { IconButton(onClick = { onRename(project, project) }) { Icon(Icons.Default.Edit, "Renommer") }; IconButton(onClick = { onDelete(project) }) { Icon(Icons.Default.Delete, "Supprimer") } } } } }

private fun toggleNomediaFile(context: Context, treeUriString: String?, showInGallery: Boolean) { if (treeUriString == null) { ensureDefaultNomedia(context, showInGallery); return }; try { val root = DocumentFile.fromTreeUri(context, treeUriString.toUri()) ?: return; val f = root.findFile(".nomedia"); if (showInGallery) f?.delete() else if (f == null) root.createFile("application/octet-stream", ".nomedia") } catch (e: Exception) { Log.e("Settings", "Erreur .nomedia SAF", e) } }