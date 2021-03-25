package dev.reeve.organization.data

import com.google.gson.GsonBuilder
import dev.reeve.organization.MainView
import dev.reeve.organization.Organizer
import javafx.collections.ObservableList
import tornadofx.*
import java.io.File
import java.util.logging.Level

class DataGrabber(val organizer: Organizer) {
	fun getGameNotes(filters: List<String>): ObservableList<GameNotes> {
		val list = mutableListOf<GameNotes>().asObservable()
		MainView.logger.log(Level.FINE, organizer.games.listFiles().size.toString())
		for (game in organizer.games.listFiles()) {
			if (game.isDirectory) {
				val dataFile = File(game, "data.json")
				
				val gameNotes = when {
					dataFile.exists() -> {
						val gson = GsonBuilder().disableHtmlEscaping()
							.registerTypeAdapter(GameNotesGson::class.java, GameNotesHandler()).create()
						val gameNotesGson = gson.fromJson(dataFile.readText(), GameNotesGson::class.java)
						val notes = GameNotes(
							game.name,
							game.lastModified(),
							dataFile,
							gameNotesGson.notes,
							gameNotesGson.playedLatest,
							gameNotesGson.walkthrough,
							gameNotesGson.url,
							gameNotesGson.artStyle,
							gameNotesGson.engineType,
							gameNotesGson.tags
						)
						if (filters.isNotEmpty()) {
							if (notes.tags != null && notes.tags.containsAll(filters.toList()))
								notes
							else
								null
						} else {
							notes
						}
						
					}
					filters.isEmpty() -> {
						GameNotes(game.name, game.lastModified(), dataFile)
					}
					else -> {
						null
					}
				}
				
				if (gameNotes != null)
					list.add(gameNotes)
			}
		}
		
		return list
	}
}