package com.makd.afinity.ui.admin.config

import com.makd.afinity.data.repository.NoActiveSessionException
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

private const val PATH_SEPARATOR = '.'
private const val LIST_SEPARATOR = ","

data class LoadedConfigForm(
    val spec: ConfigFormSpec,
    val document: JsonObject,
    val title: String? = null,
)

data class ConfigFormUiState(
    val spec: ConfigFormSpec? = null,
    val title: String? = null,
    val document: JsonObject? = null,
    val changes: Map<String, JsonElement> = emptyMap(),
    val texts: Map<String, String> = emptyMap(),
    val invalidKeys: Set<String> = emptySet(),
    val failed: Boolean = false,
    val isSaving: Boolean = false,
) {
    val isDirty: Boolean
        get() = changes.isNotEmpty() || invalidKeys.isNotEmpty()

    val conflictKeys: Set<String>
        get() =
            spec
                ?.orderedPairs
                .orEmpty()
                .filter { pair ->
                    val lower = number(pair.lowerKey)
                    val higher = number(pair.higherKey)
                    lower != null && higher != null && lower >= higher
                }
                .flatMap { listOf(it.lowerKey, it.higherKey) }
                .toSet()

    val canSave: Boolean
        get() = changes.isNotEmpty() && invalidKeys.isEmpty() && conflictKeys.isEmpty() && !isSaving

    fun flag(key: String): Boolean = flagOrNull(key) == true

    fun flagOrNull(key: String): Boolean? =
        (value(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    fun choice(key: String): String = (value(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

    fun selected(key: String): List<String> = stringList(value(key))

    fun text(key: String): String = texts[key].orEmpty()

    fun display(key: String): String =
        when (val element = value(key)) {
            is JsonArray -> stringList(element).joinToString("$LIST_SEPARATOR ")
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            else -> ""
        }

    fun isVisible(condition: FieldCondition?): Boolean =
        condition == null || choice(condition.key) in condition.values

    private fun value(key: String): JsonElement? = changes[key] ?: document?.valueAt(key)

    private fun number(key: String): Double? =
        (value(key) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
}

private fun stringList(element: JsonElement?): List<String> =
    (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun JsonObject.valueAt(path: String): JsonElement? =
    if (PATH_SEPARATOR in path) {
        (this[path.substringBefore(PATH_SEPARATOR)] as? JsonObject)?.get(
            path.substringAfter(PATH_SEPARATOR)
        )
    } else {
        this[path]
    }

private fun sameValue(original: JsonElement?, updated: JsonElement): Boolean {
    if (original == null || original is JsonNull) {
        return updated is JsonPrimitive && updated.isString && updated.content.isEmpty()
    }
    if (original is JsonPrimitive && updated is JsonPrimitive) {
        if (original.isString != updated.isString) return false
        val first = original.doubleOrNull
        val second = updated.doubleOrNull
        if (!original.isString && first != null && second != null) return first == second
    }
    return original == updated
}

private fun plainDecimal(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private fun ConfigField.initialText(document: JsonObject): String? {
    val element = document.valueAt(key)
    return when (this) {
        is NumberField -> {
            val stored = (element as? JsonPrimitive)?.longOrNull
            stored?.let { (it / storedPerShown).toString() }.orEmpty()
        }
        is DecimalField -> {
            val stored = (element as? JsonPrimitive)?.doubleOrNull
            stored?.let { plainDecimal(it / storedPerShown) }.orEmpty()
        }
        is TextInputField -> (element as? JsonPrimitive)?.contentOrNull.orEmpty()
        is NumberListField ->
            (element as? JsonArray)
                .orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.longOrNull }
                .joinToString("$LIST_SEPARATOR ")
        is SwitchField,
        is ChoiceField,
        is MultiChoiceField,
        is ReadOnlyField -> null
    }
}

private fun ConfigField.parse(text: String): JsonElement? =
    when (this) {
        is NumberField ->
            text
                .trim()
                .toLongOrNull()
                ?.takeIf { it in min..max }
                ?.let { JsonPrimitive(it * storedPerShown) }
        is DecimalField -> {
            val shown = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in min..max }
            when {
                shown == null -> null
                storedAsWholeNumber -> JsonPrimitive((shown * storedPerShown).toLong())
                else -> JsonPrimitive(shown * storedPerShown)
            }
        }
        is TextInputField -> {
            val trimmed = text.trim()
            if (trimmed.isEmpty() && required) null else JsonPrimitive(trimmed)
        }
        is NumberListField -> {
            val parts = text.split(LIST_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
            val numbers = parts.mapNotNull { it.toLongOrNull() }
            val allValid = numbers.size == parts.size && numbers.all { it in min..max }
            if (numbers.isEmpty() || !allValid) {
                null
            } else {
                JsonArray(numbers.distinct().map { JsonPrimitive(it) })
            }
        }
        is SwitchField,
        is ChoiceField,
        is MultiChoiceField,
        is ReadOnlyField -> null
    }

class ConfigFormEditor(
    private val scope: CoroutineScope,
    private val loadForm: suspend () -> Result<LoadedConfigForm>,
    private val saveChanges: suspend (Map<String, JsonElement>) -> Result<Unit>,
    initialSpec: ConfigFormSpec? = null,
) {

    private val _state = MutableStateFlow(ConfigFormUiState(spec = initialSpec))
    val state: StateFlow<ConfigFormUiState> = _state.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        scope.launch { fetch() }
    }

    fun setFlag(key: String, enabled: Boolean) {
        change(key, JsonPrimitive(enabled))
    }

    fun setChoice(field: ChoiceField, value: String) {
        val number = value.toLongOrNull()
        if (field.numeric && number != null) {
            change(field.key, JsonPrimitive(number))
        } else {
            change(field.key, JsonPrimitive(value))
        }
    }

    fun setSelected(key: String, value: String, selected: Boolean) {
        _state.update { state ->
            val current = state.selected(key)
            val updated = if (selected) (current + value).distinct() else current - value
            val original = stringList(state.document?.valueAt(key))
            val changes =
                if (updated.toSet() == original.toSet()) {
                    state.changes - key
                } else {
                    state.changes + (key to JsonArray(updated.map { JsonPrimitive(it) }))
                }
            state.copy(changes = changes)
        }
    }

    fun setText(field: ConfigField, text: String) {
        val parsed = field.parse(text)
        _state.update { state ->
            val withText = state.copy(texts = state.texts + (field.key to text))
            if (parsed == null) {
                withText.copy(
                    changes = state.changes - field.key,
                    invalidKeys = state.invalidKeys + field.key,
                )
            } else {
                withText.copy(invalidKeys = state.invalidKeys - field.key)
            }
        }
        if (parsed != null) change(field.key, parsed)
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }
        scope.launch {
            try {
                if (saveChanges(current.changes).isSuccess) {
                    _state.update { it.copy(changes = emptyMap()) }
                    fetch()
                } else {
                    _commandError.tryEmit(Unit)
                }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun change(key: String, value: JsonElement) {
        _state.update { state ->
            val changes =
                if (sameValue(state.document?.valueAt(key), value)) state.changes - key
                else state.changes + (key to value)
            state.copy(changes = changes)
        }
    }

    private suspend fun fetch() {
        loadForm()
            .onSuccess { loaded ->
                _state.update { state ->
                    val texts =
                        if (state.isDirty) {
                            state.texts
                        } else {
                            loaded.spec.fields
                                .mapNotNull { field ->
                                    field.initialText(loaded.document)?.let { field.key to it }
                                }
                                .toMap()
                        }
                    state.copy(
                        spec = loaded.spec,
                        title = loaded.title,
                        document = loaded.document,
                        texts = texts,
                        failed = false,
                    )
                }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.w(e, "Failed to load configuration")
                _state.update { it.copy(failed = it.document == null) }
            }
    }
}
