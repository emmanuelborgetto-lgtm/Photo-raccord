package com.props.photo_raccord.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.props.photo_raccord.AppDatabase
import com.props.photo_raccord.PhotoEntity
import com.props.photo_raccord.utils.deletePhotoFile
import com.props.photo_raccord.utils.updatePhotoBanner
import com.props.photo_raccord.viewmodel.GalleryViewModel
import com.props.photo_raccord.viewmodel.GalleryViewModelFactory
import com.props.photo_raccord.viewmodel.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(projet: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val factory = remember { GalleryViewModelFactory(photoDao) }
    val viewModel: GalleryViewModel = viewModel(factory = factory)
    LaunchedEffect(projet) { viewModel.initProjet(projet) }

    val photos by viewModel.photos.collectAsState()
    val decorsExistants by viewModel.decorsExistants.collectAsState()
    val filteredSortedIndexedPhotos by viewModel.filteredSortedIndexedPhotos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val scope = rememberCoroutineScope()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(projet, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onClose) { Text("Retour") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = searchQuery, onValueChange = { viewModel.searchQuery.value = it }, label = { Text("Recherche") }, modifier = Modifier.weight(1f).height(56.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, "Recherche") }, trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.searchQuery.value = "" }) { Icon(Icons.Default.Close, "Effacer") } })
                Box {
                    OutlinedButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.height(56.dp)) { Text(sortOption.displayName); Icon(Icons.Default.ArrowDropDown, "Tri") }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        SortOption.entries.forEach { option -> DropdownMenuItem(text = { Text(option.displayName) }, onClick = { viewModel.sortOption.value = option; sortMenuExpanded = false }) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (filteredSortedIndexedPhotos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (photos.isEmpty()) "Aucune photo enregistrée pour ce projet." else "Aucune photo ne correspond à votre recherche.")
                }
            } else {
                val groupedPhotosWithIndex = remember(filteredSortedIndexedPhotos, sortOption) {
                    filteredSortedIndexedPhotos.groupBy { (_, photo) ->
                        when (sortOption) {
                            SortOption.DATE_DESC, SortOption.DATE_ASC -> "Date : ${photo.date.substringBefore(" ")}"
                            SortOption.SEQUENCE_ASC, SortOption.SEQUENCE_DESC -> "Séquence ${photo.sequence}"
                            SortOption.DECOR_ASC, SortOption.DECOR_DESC -> "Décor : ${photo.decor}"
                        }
                    }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupedPhotosWithIndex.forEach { (header, indexedPhotosInGroup) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Text(header, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        items(count = indexedPhotosInGroup.size, key = { indexedPhotosInGroup[it].second.id }) { index ->
                            val globalIndex = indexedPhotosInGroup[index].first
                            PhotoCard(photo = indexedPhotosInGroup[index].second, sortOption = sortOption, onClick = { selectedPhotoIndex = globalIndex })
                        }
                    }
                }
            }
        }
        selectedPhotoIndex?.let { index ->
            if (filteredSortedIndexedPhotos.isEmpty()) selectedPhotoIndex = null
            else {
                val safeIndex = index.coerceAtMost(filteredSortedIndexedPhotos.size - 1)
                FullScreenImageDialog(photos = filteredSortedIndexedPhotos.map { it.second }, initialIndex = safeIndex, existingDecors = decorsExistants,
                    onUpdatePhoto = { updatedPhoto -> scope.launch(Dispatchers.IO) { updatePhotoBanner(context, updatedPhoto.uri, updatedPhoto.projet, updatedPhoto.sequence, updatedPhoto.decor, updatedPhoto.date); photoDao.update(updatedPhoto) } },
                    onDeletePhoto = { photoToDelete -> scope.launch(Dispatchers.IO) { deletePhotoFile(context, photoToDelete); photoDao.deletePhotos(listOf(photoToDelete)) } },
                    onDismiss = { selectedPhotoIndex = null })
            }
        }
    }
}

@Composable private fun PhotoCard(photo: PhotoEntity, sortOption: SortOption, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().height(200.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(Uri.parse(photo.uri)).size(300).crossfade(true).build(), contentDescription = "Raccord ${photo.sequence}",
                modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Crop)
            val infoText = when (sortOption) {
                SortOption.DATE_DESC, SortOption.DATE_ASC -> "Seq : ${photo.sequence} | ${photo.decor}"
                SortOption.SEQUENCE_ASC, SortOption.SEQUENCE_DESC -> "Décor : ${photo.decor}"
                SortOption.DECOR_ASC, SortOption.DECOR_DESC -> "Seq : ${photo.sequence}"
            }
            Text(infoText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(8.dp), textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable private fun FullScreenImageDialog(photos: List<PhotoEntity>, initialIndex: Int, existingDecors: List<String>, onUpdatePhoto: (PhotoEntity) -> Unit, onDeletePhoto: (PhotoEntity) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
    var isZoomedIn by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    LaunchedEffect(photos.size) { if (photos.isEmpty()) onDismiss() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, userScrollEnabled = !isZoomedIn, modifier = Modifier.fillMaxSize()) { page ->
                if (page < photos.size) ZoomableImage(photo = photos[page], isCurrentPage = page == pagerState.currentPage, onZoomChanged = { zoomed -> if (page == pagerState.currentPage) isZoomedIn = zoomed })
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
                                FabMenuItem("Éditer", Icons.Default.Edit, false) { fabExpanded = false; showEditDialog = true }
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
@Composable private fun EditPhotoDialog(photo: PhotoEntity, existingDecors: List<String>, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var sequence by remember { mutableStateOf(photo.sequence) }
    var decor by remember { mutableStateOf(photo.decor) }
    var expandedDecor by remember { mutableStateOf(false) }
    val filteredDecors = remember(decor, existingDecors) { existingDecors.filter { it.contains(decor, ignoreCase = true) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Éditer les informations") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = sequence, onValueChange = { sequence = it }, label = { Text("Séquence") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(expanded = expandedDecor && filteredDecors.isNotEmpty(), onExpandedChange = { expandedDecor = it }) {
                OutlinedTextField(value = decor, onValueChange = { newValue -> decor = newValue; expandedDecor = true }, label = { Text("Décor") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable), singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDecor) })
                DropdownMenu(expanded = expandedDecor && filteredDecors.isNotEmpty(), onDismissRequest = { expandedDecor = false }) {
                    filteredDecors.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { decor = item; expandedDecor = false }) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(sequence, decor) }, enabled = sequence.isNotBlank() && decor.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}

@Composable private fun ZoomableImage(photo: PhotoEntity, isCurrentPage: Boolean, onZoomChanged: (Boolean) -> Unit) {
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
    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(photo.uri).memoryCacheKey("${photo.uri}_${photo.sequence}_${photo.decor}").diskCacheKey("${photo.uri}_${photo.sequence}_${photo.decor}").build(),
        contentDescription = "Photo plein écran", modifier = Modifier.fillMaxSize().onSizeChanged { containerSize = it }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { tapOffset ->
                if (scale > 1f) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) } else {
                    val targetScale = 5f; val centerX = containerSize.width / 2f; val centerY = containerSize.height / 2f
                    scale = targetScale; offset = clampOffset(Offset((centerX - tapOffset.x) * (targetScale - 1f), (centerY - tapOffset.y) * (targetScale - 1f)), targetScale, containerSize); onZoomChanged(true)
                }
            }) }
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
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
}

@Composable private fun FabMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isDestructive: Boolean, onClick: () -> Unit) {
    val containerColor = if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val labelColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 2.dp) {
            Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = labelColor)
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = containerColor, contentColor = contentColor) { Icon(icon, text) }
    }
}
