package com.makd.afinity.ui.admin.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConfigFormViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
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
                    adminRepository.getServerConfig(current.source).map { document ->
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
                    adminRepository.updateServerConfig(current.source, changes)
                }
            },
            initialSpec = form?.spec,
        )
}
