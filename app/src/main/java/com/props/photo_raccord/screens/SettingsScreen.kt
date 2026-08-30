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
            if (isDcimTreeUri(selectedUri)) { pendingFolderUri = selectedUri; showDcimWarning = true }
            else applySelectedFolder(context, prefs, selectedUri, showInGallery) { customTreeUri = it }
        }
    }
    fun resetToDefault() { customTreeUri = null; prefs.edit { remove(PREF_STORAGE_TREE_URI) }; scope.launch(Dispatchers.IO) { ensureDefaultNomedia(context, showInGallery) } }
    val projets by photoDao.getDistinctProjets().collectAsState(initial = emptyList())
    var projectToRename by remember { mutableStateOf<String?>(null) }; var newProjectName by remember { mutableStateOf("") }; var projectToDelete by remember { mutableStateOf<String?>(null) }; var isCleaning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }; Spacer(Modifier.width(8.dp)); Text("PARAMÈTRES", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) }
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Palette de couleurs", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeButton("AMBER", currentTheme, onThemeChanged, Modifier.weight(1f)); ThemeButton("VIOLET", currentTheme, onThemeChanged, Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeButton("TURQUOISE", currentTheme, onThemeChanged, Modifier.weight(1f)); ThemeButton("SYSTEM", currentTheme, onThemeChanged, Modifier.weight(1f)) }
            HorizontalDivider()
            Text("Dossier de stockage", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dossier actuel : ${getDisplayFolderPath(customTreeUri)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedButton(onClick = { folderPickerLauncher.launch(null) }, Modifier.weight(1f)) { Text("Changer le dossier") }; if (!customTreeUri.isNullOrEmpty()) IconButton(onClick = { resetToDefault() }) { Icon(Icons.Default.Close, "Réinitialiser") } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Afficher les photos dans la galerie"); Switch(checked = showInGallery, onCheckedChange = { checked -> showInGallery = checked; prefs.edit { putBoolean(PREF_SHOW_IN_GALLERY, checked) }; scope.launch(Dispatchers.IO) { if (customTreeUri == null) ensureDefaultNomedia(context, checked) else toggleNomediaFile(context, customTreeUri, checked) } }) }
            } }
            HorizontalDivider(); Text("Projets enregistrés", style = MaterialTheme.typography.titleMedium)
            if (projets.isEmpty()) Text("Aucun projet enregistré.") else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { projets.forEach { proj -> ProjectCard(proj, { old, new -> projectToRename = old; newProjectName = new }, { projectToDelete = it }) } }
            HorizontalDivider(); Text("Maintenance", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { isCleaning = true; scope.launch(Dispatchers.IO) { val allPhotos = photoDao.getAllPhotosOnce(); val orphans = allPhotos.filter { photo -> try { context.contentResolver.openInputStream(photo.uri.toUri())?.use { true } ?: false; false } catch (_: Exception) { true } }; if (orphans.isNotEmpty()) photoDao.deletePhotos(orphans); withContext(Dispatchers.Main) { isCleaning = false; Toast.makeText(context, "${orphans.size} référence(s) orpheline(s) supprimée(s)", Toast.LENGTH_SHORT).show() } } }, enabled = !isCleaning, Modifier.fillMaxWidth()) { Text(if (isCleaning) "Nettoyage en cours..." else "Nettoyer les références supprimées") }
        }
    }
    projectToRename?.let { oldName -> AlertDialog(onDismissRequest = { projectToRename = null }, title = { Text("Renommer le projet") }, text = { OutlinedTextField(value = newProjectName, onValueChange = { newProjectName = it }, label = { Text("Nouveau nom") }, singleLine = true) }, confirmButton = { TextButton(onClick = { if (newProjectName.isNotBlank() && newProjectName != oldName) scope.launch { photoDao.renameProjet(oldName, newProjectName); onProjetRenamed(oldName, newProjectName); projectToRename = null } }) { Text("Valider") } }, dismissButton = { TextButton(onClick = { projectToRename = null }) { Text("Annuler") } }) }
    projectToDelete?.let { proj -> AlertDialog(onDismissRequest = { projectToDelete = null }, title = { Text("Supprimer le projet ?") }, text = { Text("Toutes les références des photos de \"$proj\" seront supprimées.") }, confirmButton = { TextButton(onClick = { scope.launch { photoDao.deleteProjet(proj); onProjetDeleted(proj); projectToDelete = null } }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { projectToDelete = null }) { Text("Annuler") } }) }
    if (showDcimWarning) AlertDialog(onDismissRequest = { showDcimWarning = false; pendingFolderUri = null }, title = { Text("Attention : dossier DCIM") }, text = { Text("Vous avez sélectionné le dossier DCIM. Le masquage des photos dans la galerie ne fonctionnera pas de manière fiable dans ce dossier. Pour masquer efficacement les photos, choisissez plutôt le dossier PhotoRaccord proposé par défaut.") }, confirmButton = { TextButton(onClick = { pendingFolderUri?.let { applySelectedFolder(context, prefs, it, showInGallery) { customTreeUri = it } }; showDcimWarning = false; pendingFolderUri = null }) { Text("Utiliser quand même") } }, dismissButton = { TextButton(onClick = { showDcimWarning = false; pendingFolderUri = null }) { Text("Annuler") } })
}

private fun isDcimTreeUri(uri: Uri): Boolean = try { val id = DocumentsContract.getTreeDocumentId(uri); val path = id.substringAfter(':', id).trim('/').split('/'); path.any { it.equals("DCIM", ignoreCase = true) } } catch (_: Exception) { uri.toString().contains(":DCIM", true) || uri.toString().contains("/DCIM", true) }
private fun applySelectedFolder(context: Context, prefs: android.content.SharedPreferences, selectedUri: Uri, showInGallery: Boolean, onApplied: (String) -> Unit) { try { context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); prefs.edit { putString(PREF_STORAGE_TREE_URI, selectedUri.toString()) }; onApplied(selectedUri.toString()); Thread { toggleNomediaFile(context, selectedUri.toString(), showInGallery) }.start() } catch (e: Exception) { Log.e("Settings", "Permission error", e) } }
private fun getDisplayFolderPath(uriString: String?): String = if (uriString == null) "PhotoRaccord (stockage privé)" else uriString.toUri().path?.substringAfterLast(":") ?: "Dossier personnalisé"
@Composable private fun ThemeButton(theme: String, currentTheme: String, onThemeChanged: (String) -> Unit, modifier: Modifier) { Button(onClick = { onThemeChanged(theme) }, modifier = modifier) { Text(when(theme) { "AMBER" -> "Ambre"; "VIOLET" -> "Violet"; "TURQUOISE" -> "Turquoise"; else -> "Système" }) } }
@Composable private fun ProjectCard(project: String, onRename: (String, String) -> Unit, onDelete: (String) -> Unit) { Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(project); Row { IconButton(onClick = { onRename(project, project) }) { Icon(Icons.Default.Edit, "Renommer") }; IconButton(onClick = { onDelete(project) }) { Icon(Icons.Default.Delete, "Supprimer") } } } } }
private fun toggleNomediaFile(context: Context, treeUriString: String?, showInGallery: Boolean) { if (treeUriString == null) { ensureDefaultNomedia(context, showInGallery); return }; try { val root = DocumentFile.fromTreeUri(context, treeUriString.toUri()) ?: return; val f = root.findFile(".nomedia"); if (showInGallery) f?.delete() else if (f == null) root.createFile("application/octet-stream", ".nomedia") } catch (e: Exception) { Log.e("Settings", "Erreur .nomedia SAF", e) } }
