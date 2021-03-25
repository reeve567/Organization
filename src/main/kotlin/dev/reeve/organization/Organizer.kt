package dev.reeve.organization

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import kotlin.collections.ArrayList

class Organizer(val icons: Boolean, location: String) {
	private var configFile: File? = null
	
	private var baseLocation = File("A:\\")
	private var _config: Config? = null
	val config: Config
		get() : Config {
			if (_config == null) {
				configFile = File(baseLocation, "config.json")
				if (!configFile!!.exists()) {
					MainView.logger.warning("No config found")
					val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
					configFile!!.createNewFile()
					_config = Config()
					_config!!.dataLocation = configFile!!
					configFile!!.writeText(gson.toJson(_config))
				} else {
					MainView.logger.info("Loaded config")
					_config = Gson().fromJson(FileReader(configFile!!), Config::class.java)
					_config!!.dataLocation = configFile!!
				}
			}
			return _config!!
		}
	
	val unorganized: File
	val games: File
	val temp: File
	val unren: File
	
	init {
		if (location != "") {
			baseLocation = File(location)
		}
		
		if (!baseLocation.exists()) {
			baseLocation = File("./")
		}
		MainView.logger.log(Level.INFO, "Base location: ${baseLocation.toPath()}")
		
		unorganized = File(baseLocation, "unorganized")
		if (!unorganized.exists()) {
			unorganized.mkdirs()
		}
		
		games = File(baseLocation, "games")
		if (!games.exists()) {
			games.mkdirs()
		}
		
		temp = File(baseLocation, ".temp")
		if (!temp.exists()) {
			temp.mkdirs()
			Files.setAttribute(temp.toPath(), "dos:hidden", true)
		}
		
		unren = File(baseLocation, "UnRen-dev.bat")
	}
	
	suspend fun sort() {
		println("Sorting...")
		var done = false
		val delete = ArrayList<File>()
		while (!done) {
			done = true
			delete.forEach {
				println("Deleted ${it.name}: ${it.deleteRecursively()}")
			}
			delete.clear()
			for (file in unorganized.listFiles()) {
				var start = Date().time
				if (file.isDirectory) {
					continue
				}
				if (file.extension != "bat" && file.extension != "exe" && file.extension != "mega") {
					val gameInfo = getGameInfo(file, config, temp, getDate = true, getIcon = icons)
					
					var name = gameInfo.name
					if (config.conversions.containsKey(name)) {
						name = config.conversions[name] ?: error("how")
					}
					
					if (name != "???" && name != "Game") {
						var difference = (Date().time - start) / 1000.0
						println("${file.name} <-> $name (${difference}s)")
						val dir = File(games, name)
						if (!dir.exists())
							dir.mkdirs()
						
						// copy / delete
						
						val newFile = File(dir, file.name)
						start = Date().time
						
						fun moveGame() {
							runBlocking {
								gameInfo.iconInputStreamData?.apply {
									setIcon(dir, gameInfo)
								} ?: {
									if (icons) {
										gameInfo.icon?.apply {
											setIcon(dir, gameInfo)
										}
									}
								}
							}
							
							gameInfo.closeable?.close()
							
							Files.move(
								file.toPath(),
								newFile.toPath(),
								StandardCopyOption.ATOMIC_MOVE,
								StandardCopyOption.REPLACE_EXISTING
							)
							newFile.setLastModified(gameInfo.lastUpdated!!.time)
							dir.setLastModified(System.currentTimeMillis())
							
							difference = (Date().time - start) / 1000.0
							
							println("${file.name} -> $name (${difference}s)")
							done = false
						}
						
						if (!newFile.exists()) {
							moveGame()
							break
						} else {
							if (newFile.totalSpace == file.totalSpace) {
								println("Already exists")
								gameInfo.closeable?.close()
								done = false
								delete.add(file)
								break
							} else {
								newFile.deleteRecursively()
								moveGame()
								break
							}
						}
					}
				}
				
			}
		}
	}
	
	suspend fun update() {
		println("Updating...")
		for (game in games.listFiles()) {
			if (game.isDirectory) {
				val icon = File(game, "icon.ico")
				//println("Checking ${game.name}...")
				if (!icon.exists()) {
					var newest: Date? = null
					var newestFile: File? = null
					for (file in game.listFiles()) {
						if (!file.isFile) {
							continue
						}
						if (file.extension != "zip" && file.extension != "7z") {
							continue
						}
						val date = getGameInfo(
							file, config, temp,
							getDate = true,
							getExe = false,
							getIcon = false,
							getIconEntry = false
						)
						if (newest == null || newest.before(date.lastUpdated)) {
							newest = date.lastUpdated
							newestFile = file
						}
					}
					if (newest != null && newestFile != null) {
						setIcon(game, getGameInfo(newestFile, config, temp, getDate = true, getIcon = icons))
					} else {
						//println("No compressed files found")
					}
				} else {
					//println("Icon exists!")
				}
			}
		}
	}
	
	fun removeIcons() {
		println("Removing...")
		for (game in games.listFiles()) {
			if (game.isDirectory) {
				val desktopIni = File(game, "desktop.ini")
				if (desktopIni.exists()) {
					desktopIni.delete()
					println("Deleted Desktop.ini -- ${game.name}")
				}
				val icon = File(game, "icon.ico")
				if (icon.exists()) {
					icon.delete()
					println("Deleted icon.ico -- ${game.name}")
				}
			}
		}
	}
	
	suspend fun checkVersions() {
		println("Checking...")
		val unplayedGames = mutableListOf<String>()
		val versions = mutableListOf<String>()
		
		for (file in games.listFiles()) {
			if (file.isDirectory) {
				val files = file.listFiles { f ->
					f.isDirectory || (f.isFile)
				}
				
				val directory = file.listFiles().first {
					it.isDirectory
				} != null
				
				if (!files.isNullOrEmpty()) {
					var newest = files[0]
					
					files.forEach {
						if (it.lastModified() > newest.lastModified()) {
							newest = it
						}
					}
					val fileName = if (newest.isDirectory) newest.name else newest.nameWithoutExtension
					val gameInfo = getGameInfo(file, config, temp, getExe = false)
					
					val pattern = "MM-dd--yyyy"
					val simpleDateFormat = SimpleDateFormat(pattern)
					val date = simpleDateFormat.format(gameInfo.lastUpdated)
					
					val version = getVersion(fileName)
					if (version == "???") {
						println("Could not get version for ${file.name} ($fileName)")
					} else {
						if (directory) {
							unplayedGames.add("[$date] ${file.name} -- $version")
						}
						
						versions.add("[$date] ${file.name} -- $version")
					}
				}
			}
		}
	}
}