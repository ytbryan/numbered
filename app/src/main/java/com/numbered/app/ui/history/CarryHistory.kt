package com.numbered.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.Commitment
import com.numbered.app.domain.CommitmentStatus
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

internal data class CarryHistory(val entries: List<Commitment>, val missingBefore: LocalDate?) {
    val carriedTimes: Int get() = (entries.size - 1).coerceAtLeast(0) + if (missingBefore != null) 1 else 0
}

/** IDs preserve renamed entries; older files can supply a unique original title and resolution time. */
internal fun carryHistory(selectedId: Long, commitments: List<Commitment>): CarryHistory? {
    val byId = commitments.associateBy { it.id }
    val selected = byId[selectedId] ?: return null
    val legacy = commitments.filter { it.status == CommitmentStatus.Carried }
        .groupBy { Triple(it.weekStart, it.title, it.resolvedAt) }
    val legacyChildren = commitments.filter { it.carriedFromId == null && it.carriedFrom != null }
        .groupBy { Triple(it.carriedFrom, it.title, it.createdAt) }
    val parents = commitments.associate { entry ->
        val parent = if (entry.carriedFromId != null) byId[entry.carriedFromId] else
            entry.carriedFrom?.let {
                val key = Triple(it, entry.title, entry.createdAt)
                legacy[key]?.singleOrNull().takeIf { legacyChildren[key]?.size == 1 }
            }
        entry.id to parent?.takeIf { it.weekStart == entry.carriedFrom && it.weekStart < entry.weekStart }
    }
    val children = commitments.filter { parents[it.id] != null }.groupBy { parents[it.id]!!.id }
    val earlier = mutableListOf(selected)
    val seen = mutableSetOf(selected.id)
    var entry = selected
    while (true) {
        val parent = parents[entry.id] ?: break
        if (!seen.add(parent.id)) break
        earlier.add(parent)
        entry = parent
    }
    val missing = entry.carriedFrom.takeIf { parents[entry.id] == null }
    val ordered = earlier.asReversed().toMutableList()
    entry = selected
    while (true) {
        val child = children[entry.id]?.singleOrNull() ?: break
        if (!seen.add(child.id)) break
        ordered.add(child)
        entry = child
    }
    return CarryHistory(ordered, missing)
}

internal data class HistoryState(val today: LocalDate, val history: CarryHistory?)

internal class CarryHistoryViewModel(container: AppContainer, id: Long) : ViewModel() {
    val state = combine(container.repository.commitments(), container.today.value) { entries, today ->
        HistoryState(today, carryHistory(id, entries))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
