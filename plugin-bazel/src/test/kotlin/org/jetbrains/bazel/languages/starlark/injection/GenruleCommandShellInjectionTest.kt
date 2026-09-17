package org.jetbrains.bazel.languages.starlark.injection

import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.bazel.languages.starlark.fixtures.StarlarkPsiTestCase
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkStringLiteralExpression
import org.jetbrains.bazel.languages.starlark.psi.expressions.arguments.StarlarkNamedArgumentExpression
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GenruleCommandShellInjectionTest : StarlarkPsiTestCase() {
  @Test
  fun `should have the shell script language available`() {
    Language.findLanguageByID(GenruleCommandShellInjector.SHELL_SCRIPT_LANGUAGE_ID).shouldNotBeNull()
  }

  @Test
  fun `should inject shell script into a triple quoted genrule cmd`() {
    // given
    myFixture.configureByText(
      "BUILD",
      listOf(
        "genrule(",
        "    name = \"gen\",",
        "    srcs = [\"in.txt\"],",
        "    outs = [\"out.txt\"],",
        "    cmd = \"\"\"",
        "set -e",
        "cat $(location in.txt) > $@",
        "\"\"\",",
        ")",
      ).joinToString("\n"),
    )

    // when
    val injected = injectedFiles("cmd")

    // then
    injected.size shouldBe 1
    injected.single().language.id shouldBe GenruleCommandShellInjector.SHELL_SCRIPT_LANGUAGE_ID
    injected.single().text shouldBe "\nset -e\ncat $(location in.txt) > $@\n"
  }

  @Test
  fun `should inject decoded shell script into a single line genrule cmd_bash`() {
    // given
    myFixture.configureByText(
      "BUILD",
      "native.genrule(name = \"gen\", outs = [\"out\"], cmd_bash = \"echo \\\"a\\\\tb\\\" > $@\\n\")\n",
    )

    // when
    val injected = injectedFiles("cmd_bash")

    // then
    injected.size shouldBe 1
    injected.single().language.id shouldBe GenruleCommandShellInjector.SHELL_SCRIPT_LANGUAGE_ID
    injected.single().text shouldBe "echo \"a\\tb\" > $@\n"
  }

  @Test
  fun `should not inject shell script into non-shell genrule commands`() {
    // given
    myFixture.configureByText(
      "BUILD",
      "genrule(name = \"gen\", outs = [\"out\"], cmd_ps = \"Write-Output a > $@\", cmd_bat = \"echo a > $@\")\n",
    )

    // when & then
    injectedFiles("cmd_ps").shouldBeEmpty()
    injectedFiles("cmd_bat").shouldBeEmpty()
  }

  @Test
  fun `should not inject shell script into cmd arguments of other rules`() {
    // given
    myFixture.configureByText(
      "BUILD",
      "my_rule(name = \"gen\", cmd = \"echo a\")\n",
    )

    // when & then
    injectedFiles("cmd").shouldBeEmpty()
  }

  private fun injectedFiles(argumentName: String): List<PsiFile> {
    val argument =
      PsiTreeUtil
        .findChildrenOfType(myFixture.file, StarlarkNamedArgumentExpression::class.java)
        .single { it.name == argumentName }
    val host = PsiTreeUtil.getChildOfType(argument, StarlarkStringLiteralExpression::class.java)
    checkNotNull(host) { "No string literal found for argument $argumentName" }
    return InjectedLanguageManager
      .getInstance(project)
      .getInjectedPsiFiles(host)
      .orEmpty()
      .map { it.first as PsiFile }
  }
}
