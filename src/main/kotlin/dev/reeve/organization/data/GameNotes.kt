package dev.reeve.organization.data

import com.google.gson.GsonBuilder
import dev.reeve.organization.Config
import javafx.beans.property.*
import javafx.collections.ObservableList
import khttp.get
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import tornadofx.*
import java.io.File
import java.net.MalformedURLException

class GameNotes(
	name: String? = null,
	modified: Long = 0,
	var dataFile: File? = null,
	notes: String? = null,
	playedLatest: Boolean = false,
	walkthrough: String? = null,
	url: String? = null,
	artStyle: ArtStyle? = null,
	engineType: EngineType? = null,
	tags: List<String>? = null,
	completed: Boolean = false
) : GameNotesGson() {
	val nameProperty = SimpleStringProperty(this, "name", name)
	override var name by nameProperty
	
	val dateProperty = SimpleLongProperty(this, "date", modified)
	var date by dateProperty
	
	val notesProperty = SimpleStringProperty(this, "notes", notes)
	override var notes by notesProperty
	
	val playedLatestProperty = SimpleBooleanProperty(this, "playedLatest", playedLatest)
	override var playedLatest by playedLatestProperty
	
	val walkthroughProperty = SimpleStringProperty(this, "walkthrough", walkthrough)
	override var walkthrough by walkthroughProperty
	
	val urlProperty = SimpleStringProperty(this, "url", url)
	override var url by urlProperty
	
	val artStyleProperty = SimpleObjectProperty<ArtStyle>(this, "artStyle", artStyle)
	override var artStyle by artStyleProperty
	
	val engineTypeProperty = SimpleObjectProperty<EngineType>(this, "engineType", engineType)
	override var engineType by engineTypeProperty
	
	val completedProperty = SimpleBooleanProperty(this, "completed", completed)
	override var completed by completedProperty
	
	fun save() {
		if (dataFile != null) {
			val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
			val json = gson.toJson(GameNotesGson(this))
			dataFile!!.writeText(json)
		}
	}
	
	val tagsProperty = SimpleListProperty(this, "tags", tags?.asObservable())
	override var tags by tagsProperty
	
	fun getFromURL(config: Config): Triple<EngineType, Boolean, ObservableList<String>>? {
		if (url != null && url != "") {
			try {
				val ret = get(url)
				val doc = Jsoup.parse(ret.text)
				
				fun elements(id: Int): Elements {
					val ret = doc.body().getElementsByAttributeValue("href", "/forums/games.2/?prefix_id=$id")
					return ret
				}
				
				var engine = EngineType.OTHER
				
				EngineType.values().forEach {
					if (elements(it.prefixID).isNotEmpty()) {
						engine = it
					}
				}
				var completed = false
				if (elements(18).isNotEmpty()) {
					completed = true
				}
				
				val list = doc.getElementsByClass("tagItem").map { element ->
					val r = element.text()
					if (!config.tags.contains(r)) {
						config.tags.add(r)
						config.tags.sort()
						config.save()
					}
					r
				}.asObservable()
				return Triple(engine, completed, list)
			} catch (ignored: Exception) {
			}
		}
		return null
	}
}
