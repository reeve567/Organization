package dev.reeve.organization

import tornadofx.*

class MainStyle: Stylesheet() {
	companion object {
		val table by cssclass()
	}
	
	init {
		table {
			fontFamily = "Consolas"
		}
	}
}