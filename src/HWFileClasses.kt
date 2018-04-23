/**
 * Classes for representing a homework document.
 */

/*
structure should look like this:
HWDocument
	Content
	Content
	Section
		Content
		Section
			Content
			Content
		Content
	Content
 */
//these classes definitely can be refactored

interface Closure {
	val outer: Closure
	val contents: MutableList<Content>
	fun addContent(content: Content) = contents.add(content)
}
interface Content


/**
 * A document holds contents and sections with additional metadata.
 */
class HWDocument(vararg contents: Content) : Closure {
	var title: String? = null
	var author: String? = null
	var date: String? = null
	var imports: MutableList<String> = ArrayList()

	fun addImports(importsString: String) {
		imports.addAll(
				importsString.split(",").map { it.trim() }
		)
	}

	fun buildImportsString(): String {
		return buildString {
			imports.forEach { append(it).append(", ") }
			removeSuffix(", ")
		}
	}

	override val outer = this
	override val contents = ArrayList<Content>()
	init {
		this.contents.addAll(contents)
	}
}


data class Line(val line: StringBuilder, val type: LineTypes, val depth: Int) : Content


/**
 * A section holds contents or inner sections with a section title.
 */
class Section(val title: StringBuilder, val isNumbered: Boolean = false,
			  override val outer: Closure, vararg contents: Content) : Content, Closure {
	override val contents = ArrayList<Content>()
	init {
		this.contents.addAll(contents)
	}
}


enum class LineTypes {
	HEADER, MATH, NORMAL, COMMENT, REGULAR_TITLE, NUMBERED_TITLE
}
