package com.makd.afinity.data.models.admin

import kotlinx.serialization.json.JsonObject

data class UserPolicyDocument(val userName: String, val policy: JsonObject)
