package dev.reeve.organization.data

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import javafx.collections.ObservableList
import tornadofx.*
import java.lang.reflect.Type

open class GameNotesGson(gameNotes: GameNotes? = null) {
	
	open var name: String = ""
	open var url: String? = ""
	open var walkthrough: String? = ""
	open var playedLatest: Boolean = false
	open var notes: String? = ""
	open var artStyle: ArtStyle? = null
	open var engineType: EngineType? = null
	open var tags: ObservableList<String>? = null
	open var completed: Boolean = false
	
	init {
		gameNotes?.let {
			name = gameNotes.name
			url = gameNotes.url
			walkthrough = gameNotes.walkthrough
			playedLatest = gameNotes.playedLatest
			notes = gameNotes.notes
			artStyle = gameNotes.artStyle
			engineType = gameNotes.engineType
			tags = gameNotes.tags
			completed = gameNotes.completed
		}
	}
	
}

class GameNotesHandler: JsonDeserializer<GameNotesGson> {
	override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GameNotesGson {
		val body = json.asJsonObject
		val obj = GameNotesGson()
		obj.name = body.get("name").asString
		obj.url = body.get("url")?.asString
		obj.walkthrough = body.get("walkthrough")?.asString
		obj.playedLatest = body.get("playedLatest")?.asBoolean ?: false
		obj.notes = body.get("notes")?.asString
		obj.artStyle = body.get("artStyle")?.asString?.let { ArtStyle.valueOf(it) }
		obj.engineType = body.get("engineType")?.asString?.let { EngineType.valueOf(it) }
		obj.tags = body.get("tags")?.asJsonArray?.map { it.asString }?.asObservable()
		obj.completed = body.get("developmentCompleted")?.asBoolean ?: false
		return obj
	}
}