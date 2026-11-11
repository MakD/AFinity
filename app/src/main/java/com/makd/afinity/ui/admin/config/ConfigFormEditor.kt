package com.makd.afinity.ui.admin.config

import com.makd.afinity.data.repository.NoActiveSessionException
import java.math.BigDecimal
import java.math.RoundingMode
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

    fun providerRows(field: ProviderListField): List<ProviderRow> {
        val order =
            if (field.orderKey == null) field.providers
            else providerOrder(field.providers, stringList(providerValue(field, field.orderKey)))
        val listed =
            stringList(providerValue(field, field.selectionKey)).map { it.lowercase() }.toSet()
        return order.map { name ->
            val isListed = name.lowercase() in listed
            ProviderRow(
                name = name,
                enabled =
                    when (field.selection) {
                        ProviderSelection.ENABLED_LIST -> isListed
                        ProviderSelection.DISABLED_LIST -> !isListed
                        ProviderSelection.NONE -> null
                    },
            )
        }
    }

    internal fun value(key: String): JsonElement? = changes[key] ?: document?.valueAt(key)

    private fun providerValue(field: ProviderListField, key: String?): JsonElement? {
        if (key == null) return null
        val entry = field.entry ?: return value(key)
        return (value(entry.listKey) as? JsonArray)
            ?.firstOrNull { it.isEntry(entry) }
            ?.let { (it as JsonObject)[key] }
    }

    private fun number(key: String): Double? =
        (value(key) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
}

private fun stringList(element: JsonElement?): List<String> =
    (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun names(values: List<String>): JsonArray = JsonArray(values.map { JsonPrimitive(it) })

private fun JsonElement.isEntry(entry: ProviderEntry): Boolean {
    val id = ((this as? JsonObject)?.get(entry.idKey) as? JsonPrimitive)?.contentOrNull
    return id.equals(entry.id, ignoreCase = true)
}

internal fun providerOrder(available: List<String>, saved: List<String>): List<String> {
    val byKey = available.associateBy { it.lowercase() }
    val known = saved.mapNotNull { byKey[it.lowercase()] }.distinct()
    return known + available.filter { it !in known }
}

private fun ConfigFormUiState.withChange(key: String, value: JsonElement): ConfigFormUiState {
    val changes =
        if (sameValue(document?.valueAt(key), value)) changes - key else changes + (key to value)
    return copy(changes = changes)
}

private fun ConfigFormUiState.withEntryValues(
    entry: ProviderEntry,
    values: Map<String, JsonElement>,
): JsonArray {
    val current = (value(entry.listKey) as? JsonArray).orEmpty()
    val index = current.indexOfFirst { it.isEntry(entry) }
    if (index < 0) {
        return JsonArray(
            current + JsonObject(mapOf(entry.idKey to JsonPrimitive(entry.id)) + values)
        )
    }
    val existing = current[index] as JsonObject
    return JsonArray(current.toMutableList().apply { set(index, JsonObject(existing + values)) })
}

private fun List<ProviderRow>.applyEdit(edit: ProviderEdit): List<ProviderRow> {
    val name =
        when (edit) {
            is ProviderEdit.Toggle -> edit.name
            is ProviderEdit.Move -> edit.name
        }
    val index = indexOfFirst { it.name == name }
    if (index < 0) return this
    return when (edit) {
        is ProviderEdit.Toggle ->
            if (this[index].enabled == null) this
            else toMutableList().apply { set(index, this[index].copy(enabled = edit.enabled)) }
        is ProviderEdit.Move -> {
            val target = index + edit.offset
            if (target !in indices) this else toMutableList().apply { add(target, removeAt(index)) }
        }
    }
}

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
        is ProviderListField,
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
            val shown =
                text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf {
                    it.toDouble() in min..max
                }
            val stored = shown?.multiply(BigDecimal.valueOf(storedPerShown))
            when {
                stored == null -> null
                storedAsWholeNumber ->
                    JsonPrimitive(stored.setScale(0, RoundingMode.HALF_UP).toLong())
                else -> JsonPrimitive(stored.toDouble())
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
        is ProviderListField,
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
        val flag = value.toBooleanStrictOrNull()
        when {
            field.boolean && flag != null -> change(field.key, JsonPrimitive(flag))
            field.numeric && number != null -> change(field.key, JsonPrimitive(number))
            else -> change(field.key, JsonPrimitive(value))
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

    fun editProvider(field: ProviderListField, edit: ProviderEdit) {
        _state.update { state ->
            val rows = state.providerRows(field)
            val updated = rows.applyEdit(edit)
            if (updated == rows) return@update state
            val listedWhen =
                when (field.selection) {
                    ProviderSelection.ENABLED_LIST -> true
                    ProviderSelection.DISABLED_LIST -> false
                    ProviderSelection.NONE -> null
                }
            val values =
                buildMap<String, JsonElement> {
                    field.orderKey?.let { put(it, names(updated.map { row -> row.name })) }
                    val selectionKey = field.selectionKey
                    if (selectionKey != null && listedWhen != null) {
                        val listed = updated.filter { it.enabled == listedWhen }.map { it.name }
                        put(selectionKey, names(listed))
                    }
                }
            val entry = field.entry
            if (entry == null) {
                values.entries.fold(state) { acc, (key, value) -> acc.withChange(key, value) }
            } else {
                state.withChange(entry.listKey, state.withEntryValues(entry, values))
            }
        }
    }

    private fun change(key: String, value: JsonElement) {
        _state.update { it.withChange(key, value) }
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
