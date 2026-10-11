package com.makd.afinity.data.models.admin

import kotlinx.serialization.json.JsonObject

data class LibraryOptionsDocument(val collectionType: String?, val options: JsonObject)
