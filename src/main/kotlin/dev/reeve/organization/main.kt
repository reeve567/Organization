package dev.reeve.organization

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
	runBlocking {
		println("Running...")
		
		if (args.isNotEmpty()) {
			val organizer = if (args.size > 1) {
				Organizer(args[1] == "icons", "")
			} else {
				Organizer(false,"")
			}
			
			when {
				args[0] == "sort" -> {
					organizer.sort()
				}
				args[0] == "update" -> {
					organizer.update()
				}
				args[0] == "check" -> {
					organizer.checkVersions()
				}
				args[0] == "remove" -> {
					organizer.removeIcons()
				}
			}
		}
	}
}