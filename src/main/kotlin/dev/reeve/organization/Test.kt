package dev.reeve.organization

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import java.io.File


fun main() {
	val test = File("D:\\.temp\\AbsoluteObedienceCrisis\\Absolute Obedience Crisis\\Game.exe")
	
	val zip = ArchiveStreamFactory().createArchiveInputStream(test.inputStream())
	
	while (true) {
		var entry = zip.nextEntry
		if (entry != null) {
			println(entry.name)
		} else {
			break
		}
	}
}