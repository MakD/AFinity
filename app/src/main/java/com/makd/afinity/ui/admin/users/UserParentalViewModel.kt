package com.makd.afinity.ui.admin.users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.jellyfin.sdk.model.api.ParentalRating
import timber.log.Timber

private const val SCHEDULE_DAY = "DayOfWeek"
private const val SCHEDULE_START = "StartHour"
private const val SCHEDULE_END = "EndHour"
private const val RATING_NAME_SEPARATOR = "/"

data class RatingOption(val name: String, val score: Int, val subScore: Int?)

data class AccessWindow(val day: String, val startHour: Double, val endHour: Double)

data class UserParentalUiState(
    val userName: String = "",
    val isAdministrator: Boolean = false,
    val policy: JsonObject? = null,
    val changes: Map<String, JsonElement> = emptyMap(),
    val ratings: List<RatingOption> = emptyList(),
    val failed: Boolean = false,
    val isSaving: Boolean = false,
) {
    val isDirty: Boolean
        get() = changes.isNotEmpty()

    val canSave: Boolean
        get() = isDirty && !isSaving

    val maxRating: RatingOption?
        get() {
            val score = int(PolicyKeys.MAX_PARENTAL_RATING) ?: return null
            val subScore = int(PolicyKeys.MAX_PARENTAL_SUB_RATING)
            return ratings.lastOrNull { it.score == score && it.subScore == subScore }
                ?: ratings.lastOrNull { it.score <= score }
        }

    val maxRatingScore: Int?
        get() = int(PolicyKeys.MAX_PARENTAL_RATING)

    val blockedUnrated: Set<String>
        get() = strings(value(PolicyKeys.BLOCK_UNRATED_ITEMS)).toSet()

    val allowedTags: List<String>
        get() = strings(value(PolicyKeys.ALLOWED_TAGS))

    val blockedTags: List<String>
        get() = strings(value(PolicyKeys.BLOCKED_TAGS))

    val schedules: List<AccessWindow>
        get() = windows(value(PolicyKeys.ACCESS_SCHEDULES))

    private fun int(key: String): Int? = (value(key) as? JsonPrimitive)?.intOrNull

    private fun value(key: String): JsonElement? = changes[key] ?: policy?.get(key)
}

private fun strings(element: JsonElement?): List<String> =
    (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun windows(element: JsonElement?): List<AccessWindow> =
    (element as? JsonArray).orEmpty().mapNotNull { entry ->
        val window = entry as? JsonObject ?: return@mapNotNull null
        val day = (window[SCHEDULE_DAY] as? JsonPrimitive)?.contentOrNull
        val start = (window[SCHEDULE_START] as? JsonPrimitive)?.doubleOrNull
        val end = (window[SCHEDULE_END] as? JsonPrimitive)?.doubleOrNull
        if (day == null || start == null || end == null) null else AccessWindow(day, start, end)
    }

internal fun groupRatings(ratings: List<ParentalRating>): List<RatingOption> {
    val grouped = mutableListOf<RatingOption>()
    ratings.forEach { rating ->
        val score = rating.ratingScore ?: return@forEach
        val last = grouped.lastOrNull()
        if (last != null && last.score == score.score && last.subScore == score.subScore) {
            val joined = last.name + RATING_NAME_SEPARATOR + rating.name
            grouped[grouped.lastIndex] = last.copy(name = joined)
        } else {
            grouped += RatingOption(rating.name, score.score, score.subScore)
        }
    }
    return grouped
}

@HiltViewModel
class UserParentalViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: UUID? =
        savedStateHandle.get<String>("userId")?.let { raw ->
            runCatching { UUID.fromString(raw) }.getOrNull()
        }

    private val _state = MutableStateFlow(UserParentalUiState())
    val state: StateFlow<UserParentalUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    init {
        load()
    }

    fun load() {
        val id = userId ?: return
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            loadUserFacts(id)
            loadRatings()
            loadPolicy(id)
        }
    }

    fun setMaxRating(option: RatingOption?) {
        val score = option?.let { JsonPrimitive(it.score) } ?: JsonNull
        val subScore = option?.subScore?.let { JsonPrimitive(it) } ?: JsonNull
        change(PolicyKeys.MAX_PARENTAL_RATING, score)
        change(PolicyKeys.MAX_PARENTAL_SUB_RATING, subScore)
    }

    fun setUnratedBlocked(type: String, blocked: Boolean) {
        val current = _state.value.blockedUnrated
        val updated = if (blocked) current + type else current - type
        _state.update { state ->
            val original = strings(state.policy?.get(PolicyKeys.BLOCK_UNRATED_ITEMS)).toSet()
            val changes =
                if (updated == original) {
                    state.changes - PolicyKeys.BLOCK_UNRATED_ITEMS
                } else {
                    val types = JsonArray(updated.sorted().map { JsonPrimitive(it) })
                    state.changes + (PolicyKeys.BLOCK_UNRATED_ITEMS to types)
                }
            state.copy(changes = changes)
        }
    }

    fun addAllowedTag(tag: String) {
        setTags(PolicyKeys.ALLOWED_TAGS, _state.value.allowedTags + tag.trim())
    }

    fun removeAllowedTag(tag: String) {
        setTags(PolicyKeys.ALLOWED_TAGS, _state.value.allowedTags - tag)
    }

    fun addBlockedTag(tag: String) {
        setTags(PolicyKeys.BLOCKED_TAGS, _state.value.blockedTags + tag.trim())
    }

    fun removeBlockedTag(tag: String) {
        setTags(PolicyKeys.BLOCKED_TAGS, _state.value.blockedTags - tag)
    }

    fun addSchedules(windows: List<AccessWindow>) {
        val valid = windows.filter { it.startHour < it.endHour }
        if (valid.isEmpty()) return
        setSchedules(_state.value.schedules + valid)
    }

    fun removeSchedule(window: AccessWindow) {
        setSchedules(_state.value.schedules - window)
    }

    fun save() {
        val id = userId ?: return
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (adminRepository.updateUserPolicy(id, current.changes).isSuccess) {
                    _state.update { it.copy(changes = emptyMap()) }
                    loadPolicy(id)
                } else {
                    _commandError.tryEmit(Unit)
                }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun setTags(key: String, tags: List<String>) {
        val updated = tags.filter { it.isNotBlank() }.distinct()
        _state.update { state ->
            val changes =
                if (updated == strings(state.policy?.get(key))) {
                    state.changes - key
                } else {
                    state.changes + (key to JsonArray(updated.map { JsonPrimitive(it) }))
                }
            state.copy(changes = changes)
        }
    }

    private fun setSchedules(schedules: List<AccessWindow>) {
        val updated = schedules.distinct()
        _state.update { state ->
            val key = PolicyKeys.ACCESS_SCHEDULES
            val changes =
                if (updated == windows(state.policy?.get(key))) {
                    state.changes - key
                } else {
                    val entries = updated.map { window ->
                        buildJsonObject {
                            put(SCHEDULE_DAY, window.day)
                            put(SCHEDULE_START, window.startHour)
                            put(SCHEDULE_END, window.endHour)
                        }
                    }
                    state.changes + (key to JsonArray(entries))
                }
            state.copy(changes = changes)
        }
    }

    private fun change(key: String, value: JsonElement) {
        _state.update { state ->
            val original = state.policy?.get(key) ?: JsonNull
            val changes =
                if (original == value) state.changes - key else state.changes + (key to value)
            state.copy(changes = changes)
        }
    }

    private suspend fun loadPolicy(id: UUID) {
        adminRepository
            .getUserPolicy(id)
            .onSuccess { policy ->
                val isAdministrator =
                    (policy[PolicyKeys.IS_ADMINISTRATOR] as? JsonPrimitive)?.contentOrNull == "true"
                _state.update {
                    it.copy(policy = policy, isAdministrator = isAdministrator, failed = false)
                }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load user policy")
                _state.update { it.copy(failed = it.policy == null) }
            }
    }

    private suspend fun loadUserFacts(id: UUID) {
        adminRepository.getUsers().onSuccess { users ->
            val user = users.firstOrNull { it.id == id } ?: return@onSuccess
            _state.update { it.copy(userName = user.name.orEmpty()) }
        }
    }

    private suspend fun loadRatings() {
        adminRepository.getParentalRatings().onSuccess { ratings ->
            _state.update { it.copy(ratings = groupRatings(ratings)) }
        }
    }
}
