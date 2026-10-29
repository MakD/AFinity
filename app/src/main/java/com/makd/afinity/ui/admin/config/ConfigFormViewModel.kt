package com.makd.afinity.ui.admin.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.data.repository.server.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val NESTED_SEPARATOR = '.'

@HiltViewModel
class ConfigFormViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val form: ServerConfigForm? =
        savedStateHandle.get<String>("formId")?.let { serverConfigForm(it) }

    private val optionsLoader = ChoiceOptionsLoader(adminRepository)

    val editor =
        ConfigFormEditor(
            scope = viewModelScope,
            loadForm = {
                val current = form
                if (current == null) {
                    Result.failure(IllegalArgumentException("Unknown settings form"))
                } else {
                    loadDocument(current).map { document ->
                        LoadedConfigForm(
                            spec = optionsLoader.resolve(current.spec),
                            document = document,
                        )
                    }
                }
            },
            saveChanges = { changes ->
                val current = form
                if (current == null) {
                    Result.failure(IllegalArgumentException("Unknown settings form"))
                } else {
                    saveDocument(current, changes)
                }
            },
            initialSpec = form?.spec,
        )

    private suspend fun loadDocument(form: ServerConfigForm): Result<JsonObject> {
        val main =
            adminRepository.getServerConfig(form.source).getOrElse {
                return Result.failure(it)
            }
        val nested =
            form.nestedSources.mapValues { (_, source) ->
                adminRepository.getServerConfig(source).getOrElse {
                    return Result.failure(it)
                }
            }
        return Result.success(JsonObject(main + nested))
    }

    private suspend fun saveDocument(
        form: ServerConfigForm,
        changes: Map<String, JsonElement>,
    ): Result<Unit> {
        val nestedChanges =
            form.nestedSources.mapValues { (name, _) ->
                changes
                    .filterKeys { it.substringBefore(NESTED_SEPARATOR) == name }
                    .mapKeys { (key, _) -> key.substringAfter(NESTED_SEPARATOR) }
            }
        val mainChanges = changes.filterKeys {
            it.substringBefore(NESTED_SEPARATOR) !in form.nestedSources
        }
        if (mainChanges.isNotEmpty()) {
            adminRepository.updateServerConfig(form.source, mainChanges).onFailure {
                return Result.failure(it)
            }
        }
        nestedChanges.forEach { (name, partial) ->
            val source = form.nestedSources.getValue(name)
            if (partial.isNotEmpty()) {
                adminRepository.updateServerConfig(source, partial).onFailure {
                    return Result.failure(it)
                }
            }
        }
        if (form.spec.id == ConfigFormIds.GENERAL && "ServerName" in mainChanges) {
            serverRepository.refreshServerInfo()
        }
        return Result.success(Unit)
    }
}
