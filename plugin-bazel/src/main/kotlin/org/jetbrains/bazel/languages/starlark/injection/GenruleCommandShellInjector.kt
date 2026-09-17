package org.jetbrains.bazel.languages.starlark.injection

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkCallExpression
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkStringLiteralExpression
import org.jetbrains.bazel.languages.starlark.psi.expressions.arguments.StarlarkNamedArgumentExpression
import org.jetbrains.bazel.languages.starlark.psi.functions.StarlarkArgumentList

/**
 * Injects the Shell Script language (provided by the bundled Shell Script plugin) into the `cmd` and `cmd_bash`
 * arguments of `genrule` calls, so that these get shell highlighting, completion, inspections and ShellCheck.
 * If the Shell Script plugin is not available, nothing is injected.
 */
class GenruleCommandShellInjector : MultiHostInjector {
  override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(StarlarkStringLiteralExpression::class.java)

  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val host = context as? StarlarkStringLiteralExpression ?: return
    if (!host.isValidHost() || !isGenruleCommand(host)) return
    val shellLanguage = Language.findLanguageByID(SHELL_SCRIPT_LANGUAGE_ID) ?: return
    registrar
      .startInjecting(shellLanguage)
      .addPlace(null, null, host, host.getStringContentsOffset())
      .doneInjecting()
  }

  private fun isGenruleCommand(host: StarlarkStringLiteralExpression): Boolean {
    val argument = host.parent as? StarlarkNamedArgumentExpression ?: return false
    val argumentName = argument.name ?: return false
    if (argumentName !in SHELL_COMMAND_ARGUMENTS) return false
    val call = (argument.parent as? StarlarkArgumentList)?.parent as? StarlarkCallExpression ?: return false
    val callName = call.name ?: return false
    return callName in GENRULE_FUNCTIONS
  }

  companion object {
    const val SHELL_SCRIPT_LANGUAGE_ID = "Shell Script"
    private val SHELL_COMMAND_ARGUMENTS = setOf("cmd", "cmd_bash")
    private val GENRULE_FUNCTIONS = setOf("genrule", "native.genrule")
  }
}
