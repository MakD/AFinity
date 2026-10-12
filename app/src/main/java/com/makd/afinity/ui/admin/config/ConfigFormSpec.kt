package com.makd.afinity.ui.admin.config

import androidx.annotation.StringRes
import com.makd.afinity.data.models.admin.ServerConfigSource

data class FieldCondition(val key: String, val values: Set<String>)

sealed interface ConfigField {
    val key: String
    val label: Int
    val summary: Int?
    val visibleWhen: FieldCondition?
}

data class SwitchField(
    override val key: String,
    @param:StringRes override val label: Int,
    @param:StringRes override val summary: Int? = null,
    override val visibleWhen: FieldCondition? = null,
) : ConfigField

data class NumberField(
    override val key: String,
    @param:StringRes override val label: Int,
    @param:StringRes override val summary: Int? = null,
    val min: Long = 0,
    val max: Long = Int.MAX_VALUE.toLong(),
    val storedPerShown: Long = 1,
    override val visibleWhen: FieldCondition? = null,
) : ConfigField

data class DecimalField(
    override val key: String,
    @param:StringRes override val label: Int,
    @param:StringRes override val summary: Int? = null,
    val min: Double = 0.0,
    val max: Double = Double.MAX_VALUE,
    val storedPerShown: Long = 1,
    val storedAsWholeNumber: Boolean = false,
    override val visibleWhen: FieldCondition? = null,
) : ConfigField

data class TextInputField(
    override val key: String,
    @param:StringRes override val label: Int,
    @param:StringRes override val summary: Int? = null,
    val required: Boolean = false,
    override val visibleWhen: FieldCondition? = null,
) : ConfigField

data class NumberListField(
    override val key: String,
    @param:StringRes override val label: Int,
    @param:StringRes override val summary: Int? = null,
    val min: Long = 1,
    val max: Long = Int.MAX_VALUE.toLong(),
    override val visibleWhen: FieldCondition? = null,
) : ConfigField

data class ChoiceOption(
    val value: String,
    @param:StringRes val label: Int? = null,
    val text: String = value,
    val visibleWhen: FieldCondition? = null,
)

enum class ChoiceOptionsSource {
    CULTURES,
    COUNTRIES,
    USERS,
}

data class ChoiceField(
    override val key: String,
    @param:StringRes override val label: Int,
    val options: List<ChoiceOption>,
    @param:StringRes override val summary: Int? = null,
    val numeric: Boolean = false,
    override val visibleWhen: FieldCondition? = null,
    val optionsSource: ChoiceOptionsSource? = null,
    val boolean: Boolean = false,
) : ConfigField

data class FlagChoice(
    val key: String,
    @param:StringRes val label: Int,
    val visibleWhen: FieldCondition? = null,
)

data class MultiChoiceField(
    override val key: String,
    @param:StringRes override val label: Int,
    val options: List<ChoiceOption>,
    @param:StringRes override val summary: Int? = null,
    override val visibleWhen: FieldCondition? = null,
    val flags: List<FlagChoice> = emptyList(),
) : ConfigField

data class ReadOnlyField(
    override val key: String,
    @param:StringRes override val label: Int,
    @param:StringRes override val summary: Int? = null,
    override val visibleWhen: FieldCondition? = null,
) : ConfigField

data class ConfigSection(
    @param:StringRes val title: Int,
    val fields: List<ConfigField>,
    val advanced: Boolean = false,
    @param:StringRes val note: Int? = null,
)

data class FieldOrder(val lowerKey: String, val higherKey: String)

data class ConfigFormSpec(
    val id: String,
    @param:StringRes val title: Int,
    val sections: List<ConfigSection>,
    @param:StringRes val note: Int? = null,
    val orderedPairs: List<FieldOrder> = emptyList(),
) {
    val fields: List<ConfigField>
        get() = sections.flatMap { it.fields }

    val hasAdvancedSections: Boolean
        get() = sections.any { it.advanced }
}

data class ServerConfigForm(
    val source: ServerConfigSource,
    val spec: ConfigFormSpec,
    val nestedSources: Map<String, ServerConfigSource> = emptyMap(),
)
