package com.makd.afinity.ui.admin.config

import com.makd.afinity.data.repository.admin.ServerAdminRepository

class ChoiceOptionsLoader(private val adminRepository: ServerAdminRepository) {

    private val loaded = mutableMapOf<ChoiceOptionsSource, List<ChoiceOption>>()

    suspend fun resolve(spec: ConfigFormSpec): ConfigFormSpec {
        val sources =
            spec.fields.filterIsInstance<ChoiceField>().mapNotNull { it.optionsSource }.toSet()
        if (sources.isEmpty()) return spec
        sources.forEach { source ->
            if (source !in loaded) fetch(source)?.let { loaded[source] = it }
        }
        return spec.copy(
            sections =
                spec.sections.map { section ->
                    section.copy(fields = section.fields.map { field -> field.withOptions() })
                }
        )
    }

    private fun ConfigField.withOptions(): ConfigField {
        val choice = this as? ChoiceField ?: return this
        val extra = choice.optionsSource?.let { loaded[it] } ?: return this
        return choice.copy(options = choice.options + extra)
    }

    private suspend fun fetch(source: ChoiceOptionsSource): List<ChoiceOption>? =
        when (source) {
            ChoiceOptionsSource.CULTURES ->
                adminRepository.getCultures().getOrNull()?.map { culture ->
                    ChoiceOption(
                        value = culture.twoLetterIsoLanguageName,
                        text = culture.displayName,
                    )
                }
            ChoiceOptionsSource.COUNTRIES ->
                adminRepository.getCountries().getOrNull()?.mapNotNull { country ->
                    val code = country.twoLetterIsoRegionName ?: return@mapNotNull null
                    ChoiceOption(value = code, text = country.displayName ?: code)
                }
            ChoiceOptionsSource.USERS ->
                adminRepository.getUsers().getOrNull()?.map { user ->
                    ChoiceOption(
                        value = user.id.toString().replace("-", ""),
                        text = user.name.orEmpty(),
                    )
                }
        }
}
