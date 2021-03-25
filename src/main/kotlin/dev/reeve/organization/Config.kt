package dev.reeve.organization

import com.google.gson.GsonBuilder
import java.io.File

class Config {
	fun save() {
		val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
		dataLocation.writeText(gson.toJson(this))
	}
	
	@Transient
	lateinit var dataLocation: File
	var conversions = emptyMap<String, String>()
	var excludedClips = emptyList<String>()
	var excludedExe = emptyList<String>()
	var tags = mutableListOf<String>()
}