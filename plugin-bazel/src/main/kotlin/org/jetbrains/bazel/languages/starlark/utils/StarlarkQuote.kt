package org.jetbrains.bazel.languages.starlark.utils

import com.intellij.openapi.util.TextRange

enum class StarlarkQuote(val quote: String) {
  SINGLE("'"),
  DOUBLE("\""),
  TRIPLE_SINGLE("'''"),
  TRIPLE_DOUBLE("\"\"\""),
  UNQUOTED(""),
  ;

  val isTriple: Boolean
    get() = this == TRIPLE_SINGLE || this == TRIPLE_DOUBLE

  fun rangeWithinQuotes(string: String): TextRange = TextRange(quote.length, string.length - quote.length)

  fun wrap(toWrap: String): String = quote + toWrap + quote

  fun unwrap(toUnwrap: String): String = toUnwrap.removeSurrounding(quote)

  companion object {
    // Triple quotes have to be checked first, as a triple quote also starts with a single quote.
    fun ofString(string: String): StarlarkQuote =
      when {
        string.startsWith(TRIPLE_SINGLE.quote) -> TRIPLE_SINGLE
        string.startsWith(TRIPLE_DOUBLE.quote) -> TRIPLE_DOUBLE
        string.startsWith(SINGLE.quote) -> SINGLE
        string.startsWith(DOUBLE.quote) -> DOUBLE
        else -> UNQUOTED
      }
  }
}
