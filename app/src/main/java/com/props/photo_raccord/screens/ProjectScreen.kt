package com.props.photo_raccord.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.props.photo_raccord.AppDatabase
import kotlinx.coroutines.flow.collectAsState

@Composable
fun ProjectSelectionScreen(currentProjet: String, onProjectSelected: (String) -> Unit) {
    val context = LocalContext.current
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val projetsExistants by photoDao.getDistinctProjets().collectAsState(initial = emptyList())
    var inputProjet by remember { mutableStateOf(currentProjet) }
    var expandedProjet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("Photo-raccord", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sélectionner ou créer un projet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))

        ExposedDropdownMenuBox(expanded = expandedProjet && projetsExistants.isNotEmpty(), onExpandedChange = { expandedProjet = it }) {
            OutlinedTextField(
                value = inputProjet, onValueChange = { inputProjet = it }, label = { Text("Nom du Projet / Film") },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                singleLine = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (inputProjet.isNotEmpty()) IconButton(onClick = { inputProjet = "" }) { Icon(Icons.Default.Close, "Effacer") }
                        if (projetsExistants.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProjet)
                    }
                }
            )
            ExposedDropdownMenu(expanded = expandedProjet && projetsExistants.isNotEmpty(), onDismissRequest = { expandedProjet = false }) {
                Text("Projets existants", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                projetsExistants.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { inputProjet = item; expandedProjet = false }) }
            }
        }

        Button(onClick = { if (inputProjet.isNotBlank()) onProjectSelected(inputProjet.trim()) }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = inputProjet.isNotBlank()) { Text("Valider") }
        Spacer(modifier = Modifier.weight(1f))
        Text("Photo-raccord v0.1 - 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
