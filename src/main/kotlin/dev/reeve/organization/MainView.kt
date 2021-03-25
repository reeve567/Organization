package dev.reeve.organization

import dev.reeve.organization.data.*
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventTarget
import javafx.geometry.Orientation
import javafx.scene.control.*
import javafx.scene.layout.Region
import javafx.scene.text.FontWeight
import khttp.get
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import tornadofx.*
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level
import java.util.logging.Logger

class MainView : View("Games") {
	private val paddingDistance = 10
	
	companion object {
		val logger = Logger.getLogger("Organization")
		
		init {
			logger.level = Level.ALL
			logger.setFilter {
				true
			}
		}
	}
	
	override val root = TabPane()
	private val model = GameNotesModel(GameNotes())
	
	private var form: Form by singleAssign()
	private var files: ListView<String> by singleAssign()
	private var tags: ListView<String> by singleAssign()
	private var filterList: EventTarget by singleAssign()
	
	private val organizer = Organizer(true, "")
	private val grabber = DataGrabber(organizer)
	private var data = grabber.getGameNotes(emptyList())
	
	init {
		with(root) {
			tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
			tab("Games") {
				borderpane {
					top {
						menubar {
							menu("File") {
								item("Refresh").action {
									data.setAll(grabber.getGameNotes(emptyList()))
								}
								item("Sort").action {
									GlobalScope.launch {
										organizer.sort()
										logger.log(Level.INFO, "Done sorting")
									}
								}
								item("Update").action {
									GlobalScope.launch {
										organizer.update()
										logger.log(Level.INFO, "Done updating")
									}
								}
								item("Refresh from URL").action {
									data.setAll(grabber.getGameNotes(emptyList()).map {
										val data = it.getFromURL(organizer.config)
										it.engineType = data?.first
										it.completed = data?.second ?: false
										it.tags = data?.third
										it
									})
								}
							}
						}
					}
					center {
						tableview(data) {
							column("Name", GameNotes::nameProperty)
							column("Played", GameNotes::playedLatestProperty)
							column("Art", GameNotes::artStyleProperty)
							column("Engine", GameNotes::engineTypeProperty)
							column("URL", GameNotes::urlProperty) {
								setCellValueFactory { cellData ->
									SimpleStringProperty(
										cellData.value.url?.let {
											if (cellData.value.url != "")
												"Y"
											else
												null
										} ?: "N")
								}
							}
							column("Completed") { table: TableColumn.CellDataFeatures<GameNotes, String> ->
								SimpleStringProperty(if (table.value.completed) "Y" else "N")
							}
							column("Date", GameNotes::date)
							
							addClass(MainStyle.table)
							
							model.rebindOnChange(this) { selectedGame ->
								form.isDisable = selectedGame == null
								item = selectedGame ?: GameNotes()
							}
							
							selectionModel.selectedItemProperty().onChange { gameNotes ->
								if (gameNotes != null) {
									files.items = gameNotes.dataFile?.parentFile?.listFiles()
										?.filter { it.extension != "ini" && it.extension != "json" && it.extension != "ico" }
										?.map { it.name }?.asObservable()
									
									tags.items = gameNotes.tags
								}
								
								
							}
						}
					}
					
					right {
						vbox(paddingDistance) {
							form {
								form = this
								isDisable = true
								fieldset(labelPosition = Orientation.VERTICAL) {
									// ADD ICON HERE
									
									label(model.name) {
										style {
											fontWeight = FontWeight.BOLD
											fontSize = 20.px
										}
									}
									hbox(5) {
										vbox {
											hbox(5) {
												button("Save") {
													enableWhen(model.dirty)
													action {
														save()
													}
												}
												button("Reset") {
													enableWhen(model.dirty)
													action {
														model.rollback()
													}
												}
												button("Open URL").action {
													(Desktop.getDesktop() ?: null)?.browse(URI(model.url.value))
												}
												button("Open folder").action {
													(Desktop.getDesktop()
														?: null)?.open(model.item.dataFile!!.parentFile)
												}
												button("Refresh from URL").action {
													val data = model.item.getFromURL(organizer.config)
													model.engineType.set(data?.first)
													model.completed.set(data?.second ?: false)
													model.item.tagsProperty.set(data?.third)
													save()
												}
											}
											field("Played Latest") {
												checkbox(property = model.playedLatest)
											}
											field("Walkthrough") {
												textfield(model.walkthrough) {
													fitToParentWidth()
												}
											}
											field("URL") {
												textfield(model.url) {
													fitToParentWidth()
												}
											}
											field("Notes & Patreon Codes") {
												textarea(model.notes)
											}
											field("Files") {
												listview<String> {
													files = this
													
													fitToParentWidth()
													
													contextmenu {
														item("Copy name") {
															action {
																val string = StringSelection(selectedItem)
																Toolkit.getDefaultToolkit().systemClipboard.setContents(
																	string,
																	string
																)
															}
														}
														item("Add Unren") {
															action {
																selectedItem?.also {
																	val selected =
																		File(model.item.dataFile!!.parentFile, it)
																	if (selected.isDirectory) {
																		Files.copy(
																			organizer.unren.toPath(),
																			File(selected, "unren.bat").toPath(),
																			StandardCopyOption.REPLACE_EXISTING
																		)
																	}
																}
															}
														}
														
													}
												}
											}
										}
										vbox(5) {
											field("Art Style") {
												togglegroup {
													vbox(paddingDistance) {
														ArtStyle.values().forEach {
															radiobutton(it.displayLabel, value = it) {
																toggleGroup = this@togglegroup
															}
														}
													}
													bind(model.artStyle)
												}
											}
											field("Engine") {
												togglegroup {
													vbox(paddingDistance) {
														EngineType.values().forEach {
															radiobutton(it.displayLabel, value = it) {
																toggleGroup = this@togglegroup
															}
														}
													}
													bind(model.engineType)
												}
											}
											field("Completed") {
												checkbox {
													bind(model.completed)
												}
											}
											field("Tags") {
												listview<String> {
													tags = this
												}
											}
										}
									}
								}
								
							}
							
						}
					}
				}
			}
			tab("Filter") {
				borderpane {
					top {
						menubar {
							menu("File") {
								item("Refresh/Reset").action {
									filterList.getChildList()?.clear()
									filterList.getChildList()?.addAll(
										organizer.config.tags.map {
											CheckBox(it)
										}
									)
								}
							}
						}
					}
					center {
						setPadding()
						vbox {
							setPadding()
							scrollpane {
								this.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
								setPadding()
								
								vbox(paddingDistance) {
									paddingHorizontal = paddingDistance
									fitToSize(this@borderpane)
									filterList = this
									
									organizer.config.tags.map {
										add(CheckBox(it))
									}
									
								}
							}
							checkbox("Filter") {
								setPadding()
								action {
									if (isSelected) {
										val checkBoxes = filterList.getChildList()?.filterIsInstance<CheckBox>()
										checkBoxes?.let {
											data.setAll(grabber.getGameNotes(checkBoxes.filter { it.isSelected }
												.map { it.text }))
										}
									} else {
										data.setAll(grabber.getGameNotes(emptyList()))
									}
								}
							}
						}
					}
					
				}
			}
		}
	}
	
	private fun save() {
		model.commit()
		
		val data = model.item
		
		data?.save()
	}
	
	private fun Region.setPadding() {
		paddingVertical = paddingDistance
		paddingHorizontal = paddingDistance
	}
}