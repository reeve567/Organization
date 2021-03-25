package dev.reeve.organization.data

import tornadofx.*

class GameNotesModel(gameNotes: GameNotes): ItemViewModel<GameNotes>(gameNotes) {
	val name = bind(GameNotes::nameProperty)
	val playedLatest = bind(GameNotes::playedLatestProperty)
	val notes = bind(GameNotes::notesProperty)
	val walkthrough = bind(GameNotes::walkthroughProperty)
	val url = bind(GameNotes::urlProperty)
	val artStyle = bind(GameNotes::artStyleProperty)
	val engineType = bind(GameNotes::engineTypeProperty)
	val completed = bind(GameNotes::completedProperty)
}