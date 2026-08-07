package com.props.photo_raccord.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.props.photo_raccord.AppDatabase
import com.props.photo_raccord.DM_Mono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    projet: String, sequence: String, decor: String,
    onSequenceChange: (String) -> Unit, onDecorChange: (String) -> Unit,
    onStartCamera: () -> Unit, onOpenGallery: () -> Unit, onBackToProjects: () -> Unit
) {
    val context = LocalContext.current
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val decorsExistants by photoDao.getDistinctDecorsParProjet(projet).collectAsState(initial = emptyList())
    var expandedDecor by remember { mutableStateOf(false) }
    val sortedDecors = remember(decor, decorsExistants) { decorsExistants.sortedByDescending { it.contains(decor, ignoreCase = true) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBackToProjects) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Changer de projet")
                }
                Column {
                    Text(
                        text = projet.uppercase(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }

        HorizontalDivider()

        // Champ Séquence
        OutlinedTextField(
            value = sequence,
            onValueChange = onSequenceChange,
            label = { Text("SÉQUENCE", fontFamily = DM_Mono) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (sequence.isNotEmpty()) {
                    IconButton(onClick = { onSequenceChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer")
                    }
                }
            }
        )

        // Champ Décor
        ExposedDropdownMenuBox(
            expanded = expandedDecor && sortedDecors.isNotEmpty(),
            onExpandedChange = { expandedDecor = it }
        ) {
            OutlinedTextField(
                value = decor,
                onValueChange = { newValue ->
                    onDecorChange(newValue)
                    expandedDecor = true
                },
                label = { Text("DÉCOR", fontFamily = DM_Mono) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                singleLine = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (decor.isNotEmpty()) {
                            IconButton(onClick = { onDecorChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer")
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDecor)
                    }
                }
            )

            ExposedDropdownMenu(
                expanded = expandedDecor && sortedDecors.isNotEmpty(),
                onDismissRequest = { expandedDecor = false }
            ) {
                sortedDecors.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item, fontFamily = DM_Mono) },
                        onClick = {
                            onDecorChange(item)
                            expandedDecor = false
                        }
                    )
                }
            }
        }

        // Boutons d'action
        Button(
            onClick = onStartCamera,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = sequence.isNotBlank() && decor.isNotBlank()
        ) {
            Text(
                text = "OUVRIR L'APPAREIL PHOTO",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = DM_Mono
            )
        }

        OutlinedButton(
            onClick = onOpenGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "VOIR LA GALERIE DU PROJET",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = DM_Mono
            )
        }
    }
}