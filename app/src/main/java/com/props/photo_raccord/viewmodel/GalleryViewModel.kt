package com.props.photo_raccord.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

    val filteredSortedIndexedPhotos = combine(
        photos, searchQuery, sortOption
    ) { list, query, sort ->
        val filtered = if (query.isEmpty()) list else {
            val lowerQuery = query.lowercase(Locale.getDefault())
            list.filter {
                it.decor.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                        it.sequence.lowercase(Locale.getDefault()).contains(lowerQuery)
            }
        }

        fun parseSeq(seq: String): Pair<Int, String> {
            val num = seq.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            val alpha = seq.filter { it.isLetter() }.lowercase(Locale.getDefault())
            return Pair(num, alpha)
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        fun parseDate(dateStr: String): Long {
            return try { dateFormat.parse(dateStr)?.time ?: 0L } catch (_: Exception) { 0L }
        }

        val sorted = when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { parseDate(it.date) }
            SortOption.DATE_ASC -> filtered.sortedBy { parseDate(it.date) }
            SortOption.SEQUENCE_ASC -> filtered.sortedWith(compareBy({ parseSeq(it.sequence).first }, { parseSeq(it.sequence).second }))
            SortOption.SEQUENCE_DESC -> filtered.sortedWith(compareByDescending<PhotoEntity> { parseSeq(it.sequence).first }.thenByDescending { parseSeq(it.sequence).second })
            SortOption.DECOR_ASC -> filtered.sortedBy { it.decor.lowercase(Locale.getDefault()) }
            SortOption.DECOR_DESC -> filtered.sortedByDescending { it.decor.lowercase(Locale.getDefault()) }
        }

        sorted.mapIndexed { index, photo -> index to photo }
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
