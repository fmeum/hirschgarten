package org.jetbrains.bazel.languages.starlark.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkStringLiteralExpression
import org.jetbrains.bazel.languages.starlark.utils.StarlarkQuote

/**
 * Allows the platform to change the contents of a Starlark string literal, e.g. when a fragment of a language
 * injected into the literal is edited. New content is escaped as needed for the kind of string literal.
 */
class StarlarkStringLiteralManipulator : AbstractElementManipulator<StarlarkStringLiteralExpression>() {
  override fun handleContentChange(
    element: StarlarkStringLiteralExpression,
    range: TextRange,
    newContent: String,
  ): StarlarkStringLiteralExpression {
    val oldText = element.text
    val escapedContent = if (element.isRaw()) newContent else escape(newContent, element.getQuote())
    val newText = oldText.substring(0, range.startOffset) + escapedContent + oldText.substring(range.endOffset)
    return element.updateText(newText) as StarlarkStringLiteralExpression
  }

  override fun getRangeInElement(element: StarlarkStringLiteralExpression): TextRange = element.getStringContentsOffset()

  companion object {
    fun escape(content: String, quote: StarlarkQuote): String {
      val quoteChar = quote.quote.firstOrNull()
      val result = StringBuilder(content.length)
      content.forEachIndexed { index, char ->
        when {
          char == '\\' -> result.append("\\\\")
          char == '\n' && !quote.isTriple -> result.append("\\n")
          char == '\r' && !quote.isTriple -> result.append("\\r")
          char == quoteChar && (!quote.isTriple || closesTripleQuote(content, index, quoteChar)) -> result.append('\\').append(char)
          else -> result.append(char)
        }
      }
      return result.toString()
    }

    /**
     * Inside a triple-quoted string, a quote character only needs escaping if it starts a run of three quotes
     * or if it is the last character (as it would then merge with the closing triple quote).
     */
    private fun closesTripleQuote(
      content: String,
      index: Int,
      quoteChar: Char,
    ): Boolean =
      index == content.lastIndex ||
        (index + 2 < content.length && content[index + 1] == quoteChar && content[index + 2] == quoteChar)
  }
}
