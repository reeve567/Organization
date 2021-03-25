package dev.reeve.organization

import de.innosystec.unrar.Archive
import de.innosystec.unrar.NativeStorage
import de.innosystec.unrar.rarfile.FileHeader
import kotlinx.coroutines.delay
import net.sf.image4j.codec.ico.ICODecoder
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.utils.IOUtils
import java.awt.image.BufferedImage
import java.io.*
import java.nio.file.Files
import java.util.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

object Archives {
	
	object Icon {
		data class Entry(val entry: ArchiveEntry, val type: IconInputType)
		data class FileHeaderEntry(val entry: FileHeader, val type: IconInputType)
		
		fun checkForIcon(entries: Iterator<ArchiveEntry>): Entry? {
			val ico = entries.asSequence().firstOrNull {
				it.name.endsWith(".ico")
			}
			
			if (ico != null) {
				println("found icon -- ${ico.name}")
				return Entry(ico, IconInputType.ICO)
			}
			
			val png = entries.asSequence().firstOrNull {
				it.name.endsWith("icon.png") || it.name.contains("window_icon.png") || it.name.endsWith("android-icon_foreground.png")
			}
			
			if (png != null) {
				println("found icon -- ${png.name}")
				return Entry(png, IconInputType.PNG)
			}
			
			return null
		}
		
		fun checkForIconRar(entries: Iterator<FileHeader>): FileHeaderEntry? {
			val ico = entries.asSequence().firstOrNull {
				it.fileNameString.endsWith(".ico")
			}
			
			if (ico != null) {
				println("found icon -- ${ico.fileNameW}")
				return FileHeaderEntry(ico, IconInputType.ICO)
			}
			
			val png = entries.asSequence().firstOrNull {
				it.fileNameString.endsWith("icon.png") || it.fileNameString.endsWith("window_icon.png") || it.fileNameString.endsWith("android-icon_foreground.png")
			}
			
			if (png != null) {
				println("found icon -- ${png.fileNameW}")
				return FileHeaderEntry(png, IconInputType.PNG)
			}
			
			return null
		}
		
		suspend fun getExe(
			archive: File,
			exeName: String,
			name: String,
			temp: File
		): File? {
			val origin = extract(archive, temp, exeName, name,true)
			val iconFile = File(origin, exeName)
			val iconFolder = File(origin, "extractedIcons")
			iconFolder.mkdirs()
			
			val command = "powershell.exe 7z e -y -o\"${iconFolder.toPath()}\" \"${iconFile.toPath()}\""
			val command1 =
				"Export-Icon -Size 64 -Type ico -Path '${iconFile.toPath()}' -Directory '${iconFolder.toPath()}'"
			
			var icons: List<File>? = null
			
			val tries = 3
			
			for (i in 1 until tries + 1) {
				if (icons != null && icons.isNotEmpty()) {
					break
				}
				delay(500)
				//println("Attempting to get icons... ($i/$tries)")
				Runtime.getRuntime().exec(command)
				icons = iconFolder.listFiles()?.filter { it.extension == "ico" }
			}
			
			val icon = when {
				icons!!.size > 1 -> {
					var biggest = -1
					var size = -1L
					icons.forEachIndexed { index, file ->
						if (size == -1L || size < file.totalSpace) {
							val ico = ICODecoder.read(file)
							if (ico.firstOrNull() != null && ico.firstOrNull()!!.height == ico.firstOrNull()!!.width) {
								biggest = index
								size = file.totalSpace
							}
						}
					}
					icons[biggest]
				}
				icons.isEmpty() -> {
					return null
				}
				else -> {
					icons.firstOrNull()
				}
			}
			
			return icon
		}
	}
	
	fun getLastUpdatedDate(dates: Iterator<Date>): Date? {
		var newest: Date? = null
		for (entry in dates) {
			if (newest == null || entry.after(newest)) {
				newest = entry
			}
		}
		return newest
	}
	
	
	/**
	 * @param exeName: full path, name, and extension for the exe
	 * @param converted: the nice name for reading
	 * @return returns the temp folder created
	 */
	fun extract(archive: File, temp: File, exeName: String, converted: String, onlyGetExe: Boolean): File {
		val folder = File(temp, converted)
		val exe = File(folder, exeName)
		return when (archive.extension) {
			"7z" -> {
				extract7z(archive, exe, folder, onlyGetExe)
			}
			"rar" -> {
				extractRar(archive, exe, folder, onlyGetExe)
			}
			else -> {
				extractCommons(archive, exe, folder, onlyGetExe)
			}
		}
	}
	
	private fun extractCommons(archive: File, exe: File, newLocation: File, onlyGetExe: Boolean): File {
		var inputStream =
			ArchiveStreamFactory().createArchiveInputStream(BufferedInputStream(FileInputStream(archive)))
		if (inputStream is ZipArchiveInputStream) {
			val field = inputStream::class.declaredMemberProperties.firstOrNull {
				it.name == "allowStoredEntriesWithDataDescriptor"
			}
			if (field == null) {
				error("could not find field")
			} else {
				field.isAccessible = true
				field as KMutableProperty<*>
				field.setter.call(inputStream, true)
			}
		}
		var foundExe = false
		var entry: ArchiveEntry?
		do {
			var done = false
			entry = inputStream.nextEntry
			if (entry == null || foundExe) {
				done = true
				continue
			}
			if (!inputStream.canReadEntryData(entry)) {
				continue
			}
			val file = File(newLocation, entry.name)
			
			if (onlyGetExe) {
				if (checkFile(file, exe))
					foundExe = true
				else
					continue
			}
			
			if (entry.isDirectory) {
				if (!file.isDirectory && !file.mkdirs()) {
					throw IOException("failed to create directory ${file.name}")
				}
			} else {
				val parent = file.parentFile
				if (!parent.isDirectory && !parent.mkdirs()) {
					throw IOException("failed to create directory ${parent.name}")
				}
				var outputStream = Files.newOutputStream(file.toPath())
				IOUtils.copy(inputStream, outputStream)
				outputStream.close()
				outputStream = null
			}
			
		} while (!done)
		
		inputStream.close()
		inputStream = null
		return newLocation
	}
	
	private fun extract7z(archive: File, exe: File, newLocation: File, onlyGetExe: Boolean): File {
		val sevenZFile = SevenZFile(archive)
		var foundExe = false
		try {
			for (entry in sevenZFile.entries) {
				val file = File(newLocation, entry.name)
				if (foundExe) break
				
				if (onlyGetExe) {
					if (checkFile(file, exe))
						foundExe = true
					else
						continue
				}
				if (entry.isDirectory) {
					if (!file.isDirectory && !file.mkdirs()) {
						throw IOException("failed to create directory ${file.name}")
					}
				} else {
					val parent = file.parentFile
					if (!parent.isDirectory && !parent.mkdirs()) {
						throw IOException("failed to create directory ${parent.name}")
					}
					var outputStream = Files.newOutputStream(file.toPath())
					IOUtils.copy(sevenZFile.getInputStream(entry), outputStream)
					outputStream.close()
					outputStream = null
				}
			}
		} catch (e: IOException) {
			if (e.localizedMessage.startsWith("Multi input/output stream coders")) {
				println("Error extracting ${archive.toPath()} because of multi stream error")
			} else {
				e.printStackTrace()
			}
		}
		
		sevenZFile.close()
		return newLocation
	}
	
	private fun extractRar(archive: File, exe: File, newLocation: File, onlyGetExe: Boolean): File {
		val rar = Archive(NativeStorage(archive))
		if (rar.isEncrypted) {
			error("RAR archive is encrypted!")
		}
		var fileHeader: FileHeader
		var foundExe = false
		while (true) {
			fileHeader = rar.nextFileHeader()
			if (fileHeader == null || foundExe) {
				break
			}
			if (fileHeader.isEncrypted) {
				error("Encrypted file in archive: ${fileHeader.fileNameW}")
			}
			if (fileHeader.isDirectory) {
				val name =
					if (fileHeader.isUnicode) {
						fileHeader.fileNameW
					} else {
						fileHeader.fileNameString
					}
				val file = File(newLocation, name)
				
				if (onlyGetExe) {
					if (checkFile(file, exe))
						foundExe = true
					else
						continue
				}
				
				if (!file.exists()) {
					val dirs = name.split("\\\\")
					var path = ""
					for (dir in dirs) {
						path += File.separator + dir
						File(newLocation, path).mkdirs()
					}
				}
			} else {
				val name = if (fileHeader.isFileHeader && fileHeader.isUnicode) {
					fileHeader.fileNameW
				} else
					fileHeader.fileNameString
				var file: File? = File(newLocation, name)
				if (!file!!.exists()) {
					val dirs = name.split("\\\\")
					var path = ""
					val size = dirs.size
					if (size >= 1) {
						for (i in 0 until dirs.size - 1) {
							path += File.separator + dirs[i]
							File(newLocation, path).mkdirs()
						}
						path += File.separator + dirs[dirs.size - 1]
						file = File(newLocation, path)
						file.createNewFile()
					} else if (size == 0) {
						file = null
					}
				}
				if (file != null) {
					var stream: FileOutputStream? = file.outputStream()
					rar.extractFile(fileHeader, stream)
					stream?.close()
					stream = null
				} else {
					error("Null file? ${fileHeader.fileNameW}")
				}
			}
		}
		
		rar.close()
		return newLocation
	}
	
	private fun checkFile(check: File, exe: File): Boolean {
		if (!check.isDirectory && check.name.contains(".exe") && !check.path.contains("/lib/")) {
			if (check.name == exe.name) {
				return true
			}
		}
		return false
	}
	
}

fun Archive.getHeaders(): Iterator<FileHeader> {
	val list = mutableListOf<FileHeader>()
	while (true) {
		val entry = nextFileHeader() ?: break
		list.add(entry)
	}
	return list.iterator()
}