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

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.props.photo_raccord.AppDatabase
import com.props.photo_raccord.BarlowCondensed
import com.props.photo_raccord.DM_Mono
import com.props.photo_raccord.PhotoEntity
import com.props.photo_raccord.utils.deletePhotoFile
import com.props.photo_raccord.utils.updatePhotoBanner
import com.props.photo_raccord.viewmodel.GalleryViewModel
import com.props.photo_raccord.viewmodel.GalleryViewModelFactory
import com.props.photo_raccord.viewmodel.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    projet: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val factory = remember { GalleryViewModelFactory(photoDao) }
    val viewModel: GalleryViewModel = viewModel(factory = factory)
    LaunchedEffect(projet) { viewModel.initProjet(projet) }

    val groupedPhotos by viewModel.groupedPhotosWithIndex.collectAsState()
    val filteredSortedIndexedPhotos by viewModel.filteredSortedIndexedPhotos.collectAsState()
    val decorsExistants by viewModel.decorsExistants.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    val sortStates = remember { mutableStateOf(mapOf<String, Boolean>()) }

    LaunchedEffect(Unit) {
        sortStates.value = mapOf(
            "date" to false,
            "sequence" to false,
            "decor" to false
        )
    }

    fun handleSortClick(sortType: String) {
        val currentState = sortStates.value[sortType] ?: false
        val newState = !currentState
        sortStates.value = sortStates.value.toMutableMap().apply { put(sortType, newState) }

        val newSortOption = when (sortType) {
            "date" -> if (newState) SortOption.DATE_ASC else SortOption.DATE_DESC
            "sequence" -> if (newState) SortOption.SEQUENCE_ASC else SortOption.SEQUENCE_DESC
            "decor" -> if (newState) SortOption.DECOR_ASC else SortOption.DECOR_DESC
            else -> SortOption.DATE_DESC
        }
        viewModel.sortOption.value = newSortOption
    }

    var selectedPhotoId by remember { mutableStateOf<Long?>(null) }
    val snapshotPhotoList = remember(filteredSortedIndexedPhotos) { filteredSortedIndexedPhotos.map { it.second } }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Text(
                        text = projet.uppercase(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = Color.Unspecified
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Rechercher...", fontFamily = DM_Mono) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Rechercher"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Effacer la recherche"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortButton(
                        label = "Date",
                        isActive = sortOption == SortOption.DATE_ASC || sortOption == SortOption.DATE_DESC,
                        isAscending = sortStates.value["date"] ?: false,
                        onClick = { handleSortClick("date") },
                        modifier = Modifier.weight(1f)
                    )
                    SortButton(
                        label = "Séquence",
                        isActive = sortOption == SortOption.SEQUENCE_ASC || sortOption == SortOption.SEQUENCE_DESC,
                        isAscending = sortStates.value["sequence"] ?: false,
                        onClick = { handleSortClick("sequence") },
                        modifier = Modifier.weight(1f)
                    )
                    SortButton(
                        label = "Décors",
                        isActive = sortOption == SortOption.DECOR_ASC || sortOption == SortOption.DECOR_DESC,
                        isAscending = sortStates.value["decor"] ?: false,
                        onClick = { handleSortClick("decor") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val gridState = rememberLazyGridState()

            if (filteredSortedIndexedPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (snapshotPhotoList.isEmpty()) "Aucune photo enregistrée pour ce projet."
                        else "Aucune photo ne correspond à votre recherche.",
                        fontFamily = DM_Mono
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
                ) {
                    groupedPhotos.forEach { (header, indexedPhotosInGroup) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = DM_Mono,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val count = indexedPhotosInGroup.size
                                Text(
                                    text = if (count > 1) "$count photos" else "$count photo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = DM_Mono
                                )
                            }
                        }
                        items(indexedPhotosInGroup, key = { it.second.id }) { pair ->
                            val photo = pair.second
                            PhotoCard(photo = photo, sortOption = sortOption, onClick = { selectedPhotoId = photo.id })
                        }
                    }
                }
            }
        }

        selectedPhotoId?.let { id ->
            val startIndex = snapshotPhotoList.indexOfFirst { it.id == id }.let { if (it >= 0) it else 0 }
            if (snapshotPhotoList.isNotEmpty()) {
                FullScreenImageDialog(
                    photos = snapshotPhotoList,
                    initialIndex = startIndex,
                    existingDecors = decorsExistants,
                    onUpdatePhoto = { updatedPhoto ->
                        scope.launch(Dispatchers.IO) {
                            updatePhotoBanner(context, updatedPhoto.uri, updatedPhoto.projet, updatedPhoto.sequence, updatedPhoto.decor, updatedPhoto.date)
                        }
                    },
                    onDeletePhoto = { photoToDelete ->
                        scope.launch(Dispatchers.IO) {
                            deletePhotoFile(context, photoToDelete)
                            photoDao.deletePhotos(listOf(photoToDelete))
                        }
                    },
                    onDismiss = { selectedPhotoId = null }
                )
            } else {
                selectedPhotoId = null
            }
        }
    }
}

@Composable
private fun SortButton(
    label: String,
    isActive: Boolean,
    isAscending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = DM_Mono,
                color = contentColor,
                maxLines = 1
            )
            Icon(
                imageVector = if (isAscending) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = if (isAscending) "Croissant" else "Décroissant",
                tint = contentColor
            )
        }
    }
}

@Composable
private fun PhotoCard(photo: PhotoEntity, sortOption: SortOption, onClick: () -> Unit) {
    val cardHeight = 190.dp
    val bannerHeight = 28.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Photo ${photo.sequence} ${photo.decor}" },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.uri)
                    .size(300)
                    .crossfade(true)
                    .build(),
                contentDescription = "Raccord ${photo.sequence}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight - bannerHeight)
                    .clipToBounds(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when (sortOption) {
                    SortOption.DATE_DESC, SortOption.DATE_ASC -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Seq : ${photo.sequence}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = BarlowCondensed,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = photo.decor,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = BarlowCondensed,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    SortOption.SEQUENCE_ASC, SortOption.SEQUENCE_DESC -> {
                        Text(
                            text = "Décor : ${photo.decor}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = BarlowCondensed,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    SortOption.DECOR_ASC, SortOption.DECOR_DESC -> {
                        Text(
                            text = "Seq : ${photo.sequence}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = BarlowCondensed,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenImageDialog(
    photos: List<PhotoEntity>,
    initialIndex: Int,
    existingDecors: List<String>,
    onUpdatePhoto: (PhotoEntity) -> Unit,
    onDeletePhoto: (PhotoEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
    var isZoomedIn by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    LaunchedEffect(photos.size) { if (photos.isEmpty()) onDismiss() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, userScrollEnabled = !isZoomedIn, modifier = Modifier.fillMaxSize()) { page ->
                if (page < photos.size) {
                    ZoomableImage(photo = photos[page], isCurrentPage = page == pagerState.currentPage, onZoomChanged = { zoomed ->
                        if (page == pagerState.currentPage) isZoomedIn = zoomed
                    })
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp).safeDrawingPadding()) {
                var fabExpanded by remember { mutableStateOf(false) }
                if (fabExpanded) Box(modifier = Modifier.fillMaxSize().clickable { fabExpanded = false }) {}
                FloatingActionButton(onClick = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer")
                }
                Box(modifier = Modifier.align(Alignment.TopEnd), contentAlignment = Alignment.TopEnd) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingActionButton(onClick = { fabExpanded = !fabExpanded }, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                            Icon(if (fabExpanded) Icons.Default.Close else Icons.Default.MoreVert, "Menu")
                        }
                        AnimatedVisibility(visible = fabExpanded, enter = fadeIn() + expandVertically(expandFrom = Alignment.Top), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)) {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FabMenuItem("Éditer", Icons.Default.Edit, false) {
                                    fabExpanded = false; showEditDialog = true
                                }
                                FabMenuItem("Partager", Icons.Default.Share, false) {
                                    fabExpanded = false
                                    if (pagerState.currentPage < photos.size) {
                                        val uri = photos[pagerState.currentPage].uri.toUri()
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                            type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }, "Partager"))
                                    }
                                }
                                FabMenuItem("Supprimer", Icons.Default.Delete, true) {
                                    fabExpanded = false
                                    if (pagerState.currentPage < photos.size) onDeletePhoto(photos[pagerState.currentPage])
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && pagerState.currentPage < photos.size) {
        val currentPhoto = photos[pagerState.currentPage]
        EditPhotoDialog(photo = currentPhoto, existingDecors = existingDecors,
            onConfirm = { newSeq, newDecor -> showEditDialog = false; onUpdatePhoto(currentPhoto.copy(sequence = newSeq, decor = newDecor)) },
            onDismiss = { showEditDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPhotoDialog(photo: PhotoEntity, existingDecors: List<String>, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var sequence by remember { mutableStateOf(photo.sequence) }
    var decor by remember { mutableStateOf(photo.decor) }
    var expandedDecor by remember { mutableStateOf(false) }
    val filteredDecors = remember(decor, existingDecors) { existingDecors.filter { it.contains(decor, ignoreCase = true) } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Éditer les informations", fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sequence,
                    onValueChange = { sequence = it },
                    label = { Text("Séquence", fontFamily = DM_Mono) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expandedDecor && filteredDecors.isNotEmpty(), onExpandedChange = { expandedDecor = it }) {
                    OutlinedTextField(
                        value = decor,
                        onValueChange = { newValue -> decor = newValue; expandedDecor = true },
                        label = { Text("Décor", fontFamily = DM_Mono) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDecor) }
                    )
                    DropdownMenu(expanded = expandedDecor && filteredDecors.isNotEmpty(), onDismissRequest = { expandedDecor = false }) {
                        filteredDecors.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item, fontFamily = DM_Mono) },
                                onClick = { decor = item; expandedDecor = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sequence, decor) }, enabled = sequence.isNotBlank() && decor.isNotBlank()) {
                Text("Enregistrer", fontFamily = DM_Mono)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", fontFamily = DM_Mono)
            }
        }
    )
}

@Composable
private fun FabMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isDestructive: Boolean, onClick: () -> Unit) {
    val containerColor = if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val labelColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 2.dp) {
            Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, fontFamily = DM_Mono, color = labelColor)
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = containerColor, contentColor = contentColor) { Icon(icon, text) }
    }
}

@Composable
private fun ZoomableImage(photo: PhotoEntity, isCurrentPage: Boolean, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) } }
    fun clampOffset(proposedOffset: Offset, currentScale: Float, size: IntSize): Offset {
        if (currentScale <= 1f || size.width == 0 || size.height == 0) return Offset.Zero
        val maxX = (size.width * (currentScale - 1f)) / 2f
        val maxY = (size.height * (currentScale - 1f)) / 2f
        return Offset(proposedOffset.x.coerceIn(-maxX, maxX), proposedOffset.y.coerceIn(-maxY, maxY))
    }

    // Taille de décodage plafonnée à ~2x la résolution d'écran : suffisant pour le zoom x5
    // tout en évitant de décoder des bitmaps inutilement lourds à chaque swipe du pager
    // (cause probable du message "Image decoding logging dropped!" en logcat).
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val decodeWidth = displayMetrics.widthPixels * 2
    val decodeHeight = displayMetrics.heightPixels * 2

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(photo.uri)
            .size(decodeWidth, decodeHeight)
            .memoryCacheKey("${photo.uri}_${photo.sequence}_${photo.decor}")
            .diskCacheKey("${photo.uri}_${photo.sequence}_${photo.decor}")
            .crossfade(true)
            .build(),
        contentDescription = "Photo plein écran",
        modifier = Modifier.fillMaxSize().onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tapOffset ->
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) } else {
                        val targetScale = 5f; val centerX = containerSize.width / 2f; val centerY = containerSize.height / 2f
                        scale = targetScale; offset = clampOffset(Offset((centerX - tapOffset.x) * (targetScale - 1f), (centerY - tapOffset.y) * (targetScale - 1f)), targetScale, containerSize); onZoomChanged(true)
                    }
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (scale <= 1f && event.changes.size == 1) continue
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            val newOffset = if (newScale > 1f) offset + panChange else Offset.Zero
                            scale = newScale; offset = clampOffset(newOffset, newScale, containerSize); onZoomChanged(newScale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        contentScale = ContentScale.Fit
    )
}