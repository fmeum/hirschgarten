package org.jetbrains.bazel.languages.starlark.psi.expressions

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafElement
import org.jetbrains.bazel.languages.starlark.injection.StarlarkStringLiteralEscaper
import org.jetbrains.bazel.languages.starlark.psi.StarlarkBaseElement
import org.jetbrains.bazel.languages.starlark.psi.StarlarkElementVisitor
import org.jetbrains.bazel.languages.starlark.psi.expressions.arguments.StarlarkNamedArgumentExpression
import org.jetbrains.bazel.languages.starlark.psi.statements.StarlarkLoadStatement
import org.jetbrains.bazel.languages.starlark.psi.statements.StarlarkLoadValue
import org.jetbrains.bazel.languages.starlark.references.BazelLabelReference
import org.jetbrains.bazel.languages.starlark.references.StarlarkClassnameReference
import org.jetbrains.bazel.languages.starlark.references.StarlarkLoadReference
import org.jetbrains.bazel.languages.starlark.references.StarlarkVisibilityReference
import org.jetbrains.bazel.languages.starlark.utils.StarlarkQuote

class StarlarkStringLiteralExpression(node: ASTNode) :
  StarlarkBaseElement(node),
  PsiLanguageInjectionHost {
  override fun acceptVisitor(visitor: StarlarkElementVisitor) = visitor.visitStringLiteralExpression(this)

  fun getStringContents(): String = getStringContentsOffset().substring(text)

  /** Range of the string contents (without the prefix and the quotes) relative to this element. */
  fun getStringContentsOffset(): TextRange {
    val literalText = text
    val quote = getQuote().quote
    val start = getPrefixLength() + quote.length
    val isTerminated = literalText.length >= start + quote.length && literalText.endsWith(quote)
    val end = if (isTerminated) literalText.length - quote.length else literalText.length
    return TextRange(start, maxOf(start, end))
  }

  fun getQuote(): StarlarkQuote = StarlarkQuote.ofString(text.substring(getPrefixLength()))

  /** Whether this is a raw string literal (`r"..."`), in which backslashes are not escape characters. */
  fun isRaw(): Boolean = text.substring(0, getPrefixLength()).contains('r', ignoreCase = true)

  /** Length of the optional `r`, `b`, `br` or `rb` prefix in front of the opening quote. */
  private fun getPrefixLength(): Int = text.indexOfFirst { it == '"' || it == '\'' }.coerceAtLeast(0)

  override fun isValidHost(): Boolean = true

  override fun updateText(text: String): PsiLanguageInjectionHost {
    val stringNode = node.firstChildNode as? LeafElement ?: return this
    stringNode.replaceWithText(text)
    return this
  }

  override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> = StarlarkStringLiteralEscaper(this)

  override fun getReference(): PsiReference? {
    if (isClassnameValue()) return StarlarkClassnameReference(this)
    if (isInVisibilityList()) return StarlarkVisibilityReference(this)
    val loadAncestor = findLoadStatement() ?: return BazelLabelReference(this, true)
    val loadedFileNamePsi = loadAncestor.getLoadedFileNamePsi() ?: return null
    val loadedFileReference = BazelLabelReference(loadedFileNamePsi, true)
    return when (loadedFileNamePsi) {
      this -> loadedFileReference
      else -> StarlarkLoadReference(this, loadedFileReference)
    }
  }

  private fun findLoadStatement(): StarlarkLoadStatement? = (parent as? StarlarkLoadValue)?.getLoadStatement()

  private fun isInVisibilityList(): Boolean =
    (parent is StarlarkListLiteralExpression && (parent.parent as? StarlarkNamedArgumentExpression)?.name == "visibility")

  private fun isClassnameValue(): Boolean = (parent as? StarlarkNamedArgumentExpression)?.name == "classname"
}
