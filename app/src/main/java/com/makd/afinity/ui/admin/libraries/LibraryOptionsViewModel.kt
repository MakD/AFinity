package com.makd.afinity.ui.admin.libraries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.ui.admin.config.ChoiceOptionsLoader
import com.makd.afinity.ui.admin.config.ConfigFormEditor
import com.makd.afinity.ui.admin.config.LoadedConfigForm
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LibraryOptionsViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val libraryName: String = savedStateHandle.get<String>("libraryName").orEmpty()

    private val optionsLoader = ChoiceOptionsLoader(adminRepository)

    val editor =
        ConfigFormEditor(
            scope = viewModelScope,
            loadForm = {
                adminRepository.getLibraryOptions(libraryName).map { document ->
                    LoadedConfigForm(
                        spec = optionsLoader.resolve(libraryOptionsForm(document.collectionType)),
                        document = document.options,
                        title = libraryName,
                    )
                }
            },
            saveChanges = { changes -> adminRepository.updateLibraryOptions(libraryName, changes) },
        )
}
