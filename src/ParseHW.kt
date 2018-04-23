import java.io.File
import java.time.LocalDate


private var document = HWDocument()
private var currentClosure: Closure = document
private var isSkipping = false	//if this is true, treat next char as is

private var closure = 0
private fun enterClosure(newClosure: Closure) {
	closure++
	currentClosure = newClosure
}
private fun exitClosure() {
	if (closure > 0)
		closure--
	currentClosure = currentClosure.outer
}

private fun resetState() {
	document = HWDocument()
	currentClosure = document
	isSkipping = false
}


fun parseHWFile(file: File): HWDocument {
	file.useLines { lines ->
		for (line in lines) {
			val lineType = getLineType(line)
			val parsedLine = parseLine(line, lineType)
			buildDocument(parsedLine, lineType)
		}
	}

	val result = document
	if (document.date == null)
		document.date = LocalDate.now().toString()
	resetState()

	return result
}


private fun buildDocument(line: StringBuilder, lineType: LineTypes) {
	val isNumberedTitle = lineType == LineTypes.NUMBERED_TITLE

	if (lineType == LineTypes.REGULAR_TITLE || isNumberedTitle) {
		val newSection = Section(line, isNumberedTitle, currentClosure)
		currentClosure.addContent(newSection)
		enterClosure(newSection)
	}
	else if (lineType == LineTypes.MATH && line.isEmpty()) {
		exitClosure()
	}
	else if (lineType == LineTypes.HEADER) {
		val parts = line.split(Regex(":"), 2)
		val key = parts[0].trim()
		val value = parts[1].trim()

		when (key) {
			"TITLE" -> document.title = value
			"AUTHOR" -> document.author = value
			"DATE" -> document.date = value
			"IMPORT" -> document.addImports(value)
		}
	}

	else
		currentClosure.addContent(Line(line, lineType, closure))
}


private fun parseLine(line: String, lineType: LineTypes): StringBuilder {

	/*
	for every char in line
		if char is \
			if next char is *
				treat chars as is until *\ found
			else
				treat next char as is
		else if following 2 chars are ##
			break, ignore rest of the line
		else if in math mode
			if char is *
				cdot
			else if char is _
				subscript
			else if char is ^
				superscript
		else
			normal char
	 */

	//the parsed line
	val parsedLine = StringBuilder()

	fun StringBuilder.parseAppend(c: Char): StringBuilder {
		//tex special chars # $ % & ~ _ ^ \ { }
		when (c) {
			'%', '&', '~' -> append('\\')
		}
		return append(c)
	}

	var isInMath = false

	var i = getLineStart(lineType)
	outer@while (i < line.length) {
		val currentChar = line[i]
		val nextChar: Char? =
				if (i == line.lastIndex)
					null
				else
					line[i+1]

		if (!isSkipping) {	//test special cases in here
			if (currentChar == '$')
				isInMath = !isInMath

			if (currentChar == '\\') {
				if (nextChar != '*')    //only skip one
					//if at the end of line, add backslash to indicate no newline in TeX
					parsedLine.append(nextChar ?: '\\')
				else
					isSkipping = true
				i += 2
				continue
			}
			else if (lineType == LineTypes.COMMENT ||
					currentChar == '#' && nextChar == '#') {
				break
			}
			else if (lineType == LineTypes.MATH || isInMath) {
				when (currentChar) {
					'*' -> parsedLine.append("\\cdot ")
					'_', '^' -> {
						if (nextChar != '{') {    //don't bother if it already has braces
							//'_' or '^' + first char + anything else
							val subscript = StringBuilder()
							subscript.append("$currentChar{$nextChar")
							//advance index for the 2 chars added
							i += 2

							val s = line.readUntil(
									' ', '+', '-', '*', '/', '(', ')', '[', ']', '{', '}', '=', ';',
									from = i)
							i += s.length
							parsedLine.append(subscript).append("$s}")
							continue@outer
						}
						else
							parsedLine.parseAppend(currentChar)
					}
					else -> parsedLine.parseAppend(currentChar)
				}
			}
			else
				parsedLine.parseAppend(currentChar)
		}
		else
			parsedLine.parseAppend(currentChar)

		//check to stop skipping
		if (currentChar == '*' && nextChar == '\\') {    //found *\
			parsedLine.deleteCharAt(parsedLine.lastIndex)
			isSkipping = false
			i += 2
			continue
		}

		i++
	}

//	handleLineType(parsedLine, lineType)

	return parsedLine
}


private fun getLineType(line: String): LineTypes {
	/*
				if start with #*
					add header
				else if start with ##
					ignore
				else if start with #
					normal text
				else if start with :
					new section
					if followed by *
						it's also numbered
				else
					math mode
				 */

	return	if (line.startsWith("#*"))
		LineTypes.HEADER
	else if (line.startsWith("##"))
		LineTypes.COMMENT
	else if (line.startsWith("#"))
		LineTypes.NORMAL
	else if (line.startsWith(":")) {
		if (line.startsWith(":*"))
			LineTypes.NUMBERED_TITLE
		else
			LineTypes.REGULAR_TITLE
	}
	else
		LineTypes.MATH
}


private fun getLineStart(lineType: LineTypes): Int {
	return when (lineType) {	//skip line type characters
		LineTypes.MATH ->
			0
		LineTypes.NORMAL,
		LineTypes.REGULAR_TITLE ->
			1
		LineTypes.HEADER,
		LineTypes.COMMENT,
		LineTypes.NUMBERED_TITLE ->
			2
	}
}


/**
 * Read this String in the specified region.
 *
 * Inclusive start, exclusive end.
 * Stops when the specified end is reached, the end of the String is reached,
 * or one of the specified Char is found, whichever occurs first.
 * @return	a new String representing the content read
 */
private fun String.readUntil(vararg chars: Char, from: Int = 0, to: Int = this.length): String {
	val builder = StringBuilder()
	if (chars.isEmpty()) return builder.toString()

	var i = from
	outer@while (i < to) {
		val current = this[i]
		for (c in chars) {
			if (current == c)
				break@outer
		}
		builder.append(current)
		i++
	}
	return builder.toString()
}


//private fun insertBoilerPlate(lines: MutableList<CharSequence>) {
//	lines.add(0, "\\documentclass{article}")
//
//	var i = 0
//	while (i < lines.size) {
//		val line = lines[i]
//		if (!line.isBlank() && !line.matches(Regex("\\\\\\w+.*"))) {    //pattern "\xxx"
//			lines.add(i++, "\\begin{document}")
//			lines.add(i, "\\maketitle")
//			break
//		}
//		i++
//	}
//
//	lines.add("\\end{document}")
//}

