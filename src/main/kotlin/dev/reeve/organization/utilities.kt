package dev.reeve.organization

import net.sf.image4j.codec.ico.ICOEncoder
import org.apache.commons.compress.utils.IOUtils
import org.ini4j.Wini
import java.io.*
import java.nio.file.Files
import java.util.*
import java.util.regex.Pattern
import javax.imageio.ImageIO

fun getVersion(string: String): String {
	if (string.isEmpty()) {
		println("Empty string")
		return "???"
	}
	if (!string.any {
			it.isDigit()
		}) return "???"
	val pattern = Pattern.compile("([A-Za-z])?[0-9]+[.]([A-Za-z0-9]+)?.?([A-Za-z0-9]+)?.?([A-Za-z0-9]+)?")
	val matcher = pattern.matcher(string)
	
	if (matcher.find()) {
		return string.substring(matcher.start(), matcher.end())
	}
	
	return "???"
}

fun convertName(string: String, config: Config): String {
	if (string.isEmpty()) {
		println("Empty string")
		return "???"
	}
	return string
		.replace("/Game.exe", "")
		.split('/').last()
		.replace(".exe", "")
		.replace("'", "")
		.replace("-", "_")
		.replace(" ", "_")
		.split("_").filter {
			for (str in config.excludedClips) {
				if (it.contains(str, true)) {
					return@filter false
				}
			}
			return@filter true
		}.joinToString(separator = "") {
			if (it.isNotEmpty()) {
				it.first().toUpperCase() + it.substring(1)
			}
			it
		}.let { s ->
			if (s.contains('.')) {
				return@let s.split('.').first().let {
					var temp = it
					val pattern = Pattern.compile("[0-9]|[Vv]")
					while (pattern.matcher(temp.substring(temp.lastIndex)).find()) {
						temp = temp.substring(0, temp.lastIndex)
					}
					temp
				}
			}
			return@let s
		}.also {
			if (config.conversions.containsKey(it)) {
				return config.conversions[it] ?: error("how did you fuck this up")
			}
			return it
		}
	
}

fun setIcon(dir: File, gameInfo: GameInfo) {
	val icon = File(dir, "icon.ico")
	
	if (gameInfo.icon != null) {
		println("icon!")
		Files.copy(gameInfo.icon.toPath(), icon.toPath())
	} else {
		gameInfo.iconInputStreamData?.also {
			println("icon!")
			if (it.type == IconInputType.PNG) {
				val bufferedImage = ImageIO.read(it.iconInputStream)
				ICOEncoder.write(bufferedImage, icon)
			} else {
				var output: FileOutputStream? = icon.outputStream()
				IOUtils.copy(it.iconInputStream, output)
				output?.close()
				output = null
			}
			it.iconInputStream?.close()
			it.iconInputStream = null
		}
	}
	
	val ini = File(dir, "desktop.ini")
	ini.delete()
	ini.createNewFile()
	val wini = Wini(ini)
	wini.put(".ShellClassInfo", "IconResource", "icon.ico,0")
	wini.put("ViewState", "Mode", "")
	wini.put("ViewState", "Vid", "")
	wini.put("ViewState", "FolderType", "Generic")
	wini.store()
	
	Runtime.getRuntime().exec("attrib +h +s ${ini.path}")
	Runtime.getRuntime().exec("attrib -h +s ${dir.path}")
}

fun runPowershellCommand(command: String): Process {
	return Runtime.getRuntime().exec("powershell.exe $command").also {
		it.waitFor()
	}
}