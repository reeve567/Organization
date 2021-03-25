package dev.reeve.organization

import de.innosystec.unrar.Archive
import de.innosystec.unrar.NativeStorage
import de.innosystec.unrar.io.ReadOnlyAccessInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.File
import java.util.*

data class GameInfo(
	val name: String = "???",
	val lastUpdated: Date? = null,
	val icon: File? = null,
	val iconInputStreamData: IconInputStreamData? = null,
	val closeable: Closeable? = null
)

var closeable: Closeable? = null
var run = mutableMapOf<String, Int>()

/**
 * Looks through an archive/folder for the name of the game and the data associated with it
 *
 * @param archive: The archive/folder that contains a game
 * @param config: The config values
 * @param temp: The temp folder for temporary files
 * @return info for the game, depending on getDate, getExe, and getIcon
 * @see GameInfo
 */
suspend fun getGameInfo(
	archive: File,
	config: Config,
	temp: File,
	getDate: Boolean = true,
	getExe: Boolean = true,
	getIcon: Boolean = false,
	getIconEntry: Boolean = true,
): GameInfo {
	if (archive.isDirectory) {
		/*val exe = file.listFiles().filter {
			it.extension == "exe" && !config!!.excludedExe.contains(it.name)
		}
		if (exe.isEmpty()) {
			for (folder in file.listFiles().filter {
				it.isDirectory
			}) {
				folder.listFiles().filter {
					it.extension == "exe" && !config!!.excludedExe.contains(it.name)
				}.firstOrNull()?.run {
					if (this.name.contains("Game"))
						return convertName(this.parentFile.name)
					return convertName(this.name)
				}
			}
		} else {
			if (exe.first().name.contains("Game"))
				return convertName(exe.first().parentFile.name)
			return convertName(exe.first().name)
		}*/
		return GameInfo()
	} else {
		when (archive.extension) {
			"zip" -> {
				closeable = ZipFile(archive)
				
				val zipFile = closeable as ZipFile
				/*if (archiveInputStream is ZipArchiveInputStream) {
					val field = archiveInputStream::class.declaredMemberProperties.first {
						it.name == "allowStoredEntriesWithDataDescriptor"
					}
					if (field == null) {
						error("could not find field")
					} else {
						field.isAccessible = true
						field as KMutableProperty<*>
						field.setter.call(archiveInputStream, true)
					}
				}*/
				val newest = if (getDate) {
					Archives.getLastUpdatedDate(zipFile.entries.asSequence().map { it.lastModifiedDate }.iterator())
				} else {
					null
				}
				
				val _icon = if (getIconEntry) Archives.Icon.checkForIcon(zipFile.entries.iterator()) else null
				val icon = if (_icon != null) {
					IconInputStreamData(zipFile.getInputStream(_icon.entry as ZipArchiveEntry), _icon.type)
				} else null
				
				if (getExe) {
					for (entry in zipFile.entries) {
						if (zipFile.canReadEntryData(entry)) {
							val gameInfo = entryCheck(
								entry.name,
								entry.isDirectory,
								archive,
								temp,
								config,
								newest,
								getIcon,
								icon
							)
							if (gameInfo != null) {
								return gameInfo
							}
						}
					}
				}
				return GameInfo(convertName(archive.name, config), newest, null, icon, zipFile)
			}
			"7z" -> {
				closeable = SevenZFile(archive)
				
				val sevenZFile = closeable as SevenZFile
				
				val newest = if (getDate) {
					Archives.getLastUpdatedDate(sevenZFile.entries.map { it.lastModifiedDate }.iterator())
				} else {
					null
				}
				
				/*Archives.Icon.checkForIcon(sevenZFile.entries.iterator())*/
				// TODO: 1/4/2021 figure out why the hell the IconInputStreamData line takes ages
				val _icon: Archives.Icon.Entry? = null
				val icon = if (_icon != null) {
					IconInputStreamData(
						sevenZFile.getInputStream(
							_icon.entry as SevenZArchiveEntry)
						, _icon.type)
				} else null
				
				if (getExe) {
					for (entry in sevenZFile.entries) {
						val gameInfo = entryCheck(
							entry.name,
							entry.isDirectory,
							archive,
							temp,
							config,
							newest,
							getIcon,
							icon
						)
						if (gameInfo != null) {
							return gameInfo
						}
					}
				}
				return GameInfo(convertName(archive.name, config), newest, null, icon, sevenZFile)
			}
			"rar" -> {
				println(archive.name)
				closeable = Archive(NativeStorage(archive))
				
				val rar = closeable as Archive
				
				val newest =
					if (getDate) {
						Archives.getLastUpdatedDate(rar.getHeaders().asSequence().map { it.mTime }.iterator())
					} else {
						null
					}
				
				val _icon = if (getIconEntry) Archives.Icon.checkForIconRar(rar.fileHeaders.iterator()) else null
				val icon = if (_icon != null) {
					IconInputStreamData(
						ReadOnlyAccessInputStream(
							NativeStorage(archive).read(),
							_icon.entry.positionInFile,
							_icon.entry.positionInFile + _icon.entry.dataSize
						), _icon.type
					)
				} else null
				
				if (getExe) {
					for (entry in rar.getHeaders()) {
						val gameInfo = entryCheck(
							entry.fileNameW,
							entry.isDirectory,
							archive,
							temp,
							config,
							newest,
							getIcon,
							icon
						)
						if (gameInfo != null) {
							return gameInfo
						}
					}
				}
				return GameInfo(convertName(archive.name, config), newest, null, icon, rar)
			}
		}
	}
	return GameInfo()
}

/**
 * Checks to see if the entry into the archive is the file we're looking for
 *
 * @param isDirectory: Whether or not the entry is a directory
 * @return Returns a GameInfo instance if the entry passed in is the right one
 */
private suspend fun entryCheck(
	entryName: String,
	isDirectory: Boolean,
	archive: File,
	temp: File,
	config: Config,
	newest: Date?,
	getIcon: Boolean,
	iconEntry: IconInputStreamData?
): GameInfo? {
	if (!isDirectory && entryName.contains(".exe") && !entryName.contains("/lib/")) {
		if (!config.excludedExe.any { entryName.contains(it) }) {
			val convertedName = convertName(entryName, config)
			println("$entryName -> $convertedName")
			if (getIcon) {
				closeable?.close()
				closeable = null
			}
			return GameInfo(
				convertedName,
				newest,
				if (getIcon)
					Archives.Icon.getExe(archive, entryName, convertedName, temp)
				else
					null,
				if (!getIcon)
					iconEntry
				else
					null
			)
		}
	}
	return null
}