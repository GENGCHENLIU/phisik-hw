/**
 * This file provides functions that primarily deals with converting parsed HW files to
 * TeX files and writing the resulting TeX file. This is the entry point of the program.
 *
 * @version 1.3
 */

import java.io.File
import java.util.*
import kotlin.collections.ArrayList


fun main(args: Array<String>) {
	if (args.isEmpty()) {
		println("Version 1.3")
		println()
		println("Usage:")
		println("java ... file [files]")
		println()
		println("I convert HW files to TEX files")
		System.exit(0)
	}

	for (fileName in args) {
		val hwFile = File(fileName)
		val texFileName = hwFile.nameWithoutExtension + ".tex"
		val texFile = File(texFileName)

		if (!prepareFile(texFile)) {
			System.err.println("Filed to create file $fileName")
			continue
		}

		println("Parsing file $fileName")

		val texFileContent = LinkedList<CharSequence>()
		try {
			val hwDocument = parseHWFile(hwFile)

			var closure = 0
			fun Closure.toLinearList(): List<Line> {	//this fun grew way longer than i thought
				val lines = ArrayList<Line>()
				contents.forEach {
					if (it is Closure) {
						if (it is Section) {
							lines.add(
									Line(
											it.title,
											if (it.isNumbered) LineTypes.NUMBERED_TITLE
											else LineTypes.REGULAR_TITLE,
											closure
									)
							)
						}
						closure++
						lines.addAll(it.toLinearList())
						closure--
					}
					else if (it is Line)
						lines.add(it)
				}
				return lines
			}

			val lines = hwDocument.toLinearList()

			//handle headers and other boiler plate first
			texFileContent.add("\\documentclass{article}")

			val title = hwDocument.title ?: ""
			texFileContent.add("\\title{$title}")
			val author = hwDocument.author ?: ""
			texFileContent.add("\\author{$author}")
			val date = hwDocument.date ?: ""
			texFileContent.add("\\date{$date}")
			val importsString = hwDocument.buildImportsString()
			texFileContent.add("\\usepackage{$importsString}")

			texFileContent.add("\\begin{document}")
			texFileContent.add("\\maketitle")

			//translate rest of the file
			lines.forEach { texFileContent.add(translateToTEX(it)) }

			texFileContent.add("\\end{document}")

		}
		catch (e: Exception) {
			System.err.println("Something went wrong parsing file $fileName")
			e.printStackTrace()
			continue
		}

		texFile.bufferedWriter().use { writer ->
			texFileContent.forEach {
				writer.write(it.toString())
				writer.newLine()
			}
		}

		println("Wrote to file $texFileName")
	}
}


private fun prepareFile(file: File): Boolean {
	val parent = file.parentFile
	if (parent?.mkdirs() == false)
		return false

//	if (!file.createNewFile())
//		return false
	file.createNewFile()
	return true
}


private fun translateToTEX(line: Line): StringBuilder {
	val (lineStr, lineType, depth) = line
	val texLine = StringBuilder(lineStr)

	when (lineType) {
		LineTypes.HEADER,
		LineTypes.COMMENT -> {}
		LineTypes.MATH ,
		LineTypes.NORMAL -> {
			if (lineType == LineTypes.MATH)
				texLine.insert(0, '$').append('$')

			//line continuation
			//if line not ends with '\', append "\\"
			if (!lineStr.endsWith('\\'))
				texLine.append("\\\\")
		}
		LineTypes.REGULAR_TITLE,
		LineTypes.NUMBERED_TITLE -> {
			val subs = StringBuilder()	//build the right number of "sub"s
			val d = if (depth > 3) 3 else depth
			for (i in 0 until d)
				subs.append("sub")
			val ast = if (lineType != LineTypes.NUMBERED_TITLE) "*" else ""
			texLine.insert(0, "\\${subs}section$ast{").append("}")
		}
	}

	return texLine
}

