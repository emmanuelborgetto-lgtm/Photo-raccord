package com.props.photo_raccord.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.props.photo_raccord.PhotoDao
import com.props.photo_raccord.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Locale

// Options de tri pour la galerie
enum class SortOption(val displayName: String) {
    DATE_DESC("Plus récentes"),
    DATE_ASC("Plus anciennes"),
    SEQUENCE_ASC("Séquence ↑"),
    SEQUENCE_DESC("Séquence ↓"),
    DECOR_ASC("Décor A-Z"),
    DECOR_DESC("Décor Z-A")
}

class GalleryViewModel(private val photoDao: PhotoDao) : ViewModel() {
    private val _projet = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")
    val sortOption = MutableStateFlow(SortOption.DATE_DESC)

    fun initProjet(projet: String) {
        _projet.value = projet
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<PhotoEntity>> = _projet
        .flatMapLatest { p ->
            if (p.isNotBlank()) photoDao.getPhotosParProjet(p) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val decorsExistants: StateFlow<List<String>> = _projet
        .flatMapLatest { p ->
            if (p.isNotBlank()) photoDao.getDistinctDecorsParProjet(p) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ThreadLocal formatter to avoid reallocating SimpleDateFormat on each recomputation
    private val dateFormatThreadLocal = ThreadLocal.withInitial { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    /**
     * Core pipeline:
     * - filteredSortedIndexedPhotos: List of (sortedPosition, PhotoEntity) already filtered and sorted.
     *   Sorting/parsing work occurs here once per emission.
     * - groupedPhotosWithIndex: grouping of the sorted list, computed on Default dispatcher.
     */
    val filteredSortedIndexedPhotos = combine(photos, searchQuery, sortOption) { list, query, sort ->
        // Precompute parse results once per item
        data class Enriched(val originalIndex: Int, val photo: PhotoEntity, val parsedDate: Long, val parsedSeq: Pair<Int, String>)

        fun parseSeqOnce(seq: String): Pair<Int, String> {
            val num = seq.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            val alpha = seq.filter { it.isLetter() }.lowercase(Locale.getDefault())
            return Pair(num, alpha)
        }

        fun parseDateOnce(dateStr: String): Long {
            return try {
                dateFormatThreadLocal.get().parse(dateStr)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }

        val filtered = if (query.isEmpty()) list else {
            val lowerQuery = query.lowercase(Locale.getDefault())
            list.filter {
                it.decor.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                        it.sequence.lowercase(Locale.getDefault()).contains(lowerQuery)
            }
        }

        val enrichedList = filtered.mapIndexed { idx, photo ->
            Enriched(idx, photo, parseDateOnce(photo.date), parseSeqOnce(photo.sequence))
        }

        val sortedEnriched = when (sort) {
            SortOption.DATE_DESC -> enrichedList.sortedByDescending { it.parsedDate }
            SortOption.DATE_ASC -> enrichedList.sortedBy { it.parsedDate }
            SortOption.SEQUENCE_ASC -> enrichedList.sortedWith(compareBy({ it.parsedSeq.first }, { it.parsedSeq.second }))
            SortOption.SEQUENCE_DESC -> enrichedList.sortedWith(compareByDescending<Enriched> { it.parsedSeq.first }.thenByDescending { it.parsedSeq.second })
            SortOption.DECOR_ASC -> enrichedList.sortedBy { it.photo.decor.lowercase(Locale.getDefault()) }
            SortOption.DECOR_DESC -> enrichedList.sortedByDescending { it.photo.decor.lowercase(Locale.getDefault()) }
        }

        // IMPORTANT CHANGE: return the index in the sorted list (newIndex) so UI indexes match sorted order.
        sortedEnriched.mapIndexed { newIndex, enriched -> newIndex to enriched.photo }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Grouped list exposed to UI:
     * List of pairs (header string, list of (sortedPosition, PhotoEntity) in that group)
     *
     * This runs on Dispatchers.Default to avoid grouping on the UI thread.
     * We combine with sortOption so the header formatting (which depends on sort) is correct.
     */
    val groupedPhotosWithIndex: StateFlow<List<Pair<String, List<Pair<Int, PhotoEntity>>>>> =
        combine(filteredSortedIndexedPhotos, sortOption) { sortedList, sort ->
            // Build grouping key depending on current sort option
            val groups = when (sort) {
                SortOption.DATE_DESC, SortOption.DATE_ASC -> {
                    sortedList.groupBy { (_, photo) -> "Date : ${photo.date.substringBefore(" ")}" }
                }
                SortOption.SEQUENCE_ASC, SortOption.SEQUENCE_DESC -> {
                    sortedList.groupBy { (_, photo) -> "Séquence ${photo.sequence}" }
                }
                SortOption.DECOR_ASC, SortOption.DECOR_DESC -> {
                    sortedList.groupBy { (_, photo) -> "Décor : ${photo.decor}" }
                }
            }
            // Preserve group ordering as in sortedList (to keep list order stable)
            val orderedKeys = sortedList.map { (_, photo) ->
                when (sort) {
                    SortOption.DATE_DESC, SortOption.DATE_ASC -> "Date : ${photo.date.substringBefore(" ")}"
                    SortOption.SEQUENCE_ASC, SortOption.SEQUENCE_DESC -> "Séquence ${photo.sequence}"
                    SortOption.DECOR_ASC, SortOption.DECOR_DESC -> "Décor : ${photo.decor}"
                }
            }.distinct()

            orderedKeys.mapNotNull { key ->
                groups[key]?.let { key to it }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class GalleryViewModelFactory(private val dao: PhotoDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}