/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineFunction

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.openapiext.isUnitTestMode

class RsInlineFunctionHandler : InlineActionHandler() {
    override fun isEnabledOnElement(element: PsiElement): Boolean = canInlineElement(element)

    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        val function = element as RsFunction

        val reference = TargetElementUtil.findReference(editor, editor.caretModel.offset)

        if (RsInlineFunctionProcessor.doesFunctionHaveMultipleReturns(function)) {
            errorHint(project, editor, "Cannot inline function with more than one exit points")
            return
        }

        var allowInlineThisOnly = false
        if (RsInlineFunctionProcessor.isFunctionRecursive(function)) {
            if (reference != null) {
                allowInlineThisOnly = true
            } else {
                errorHint(project, editor, "Cannot inline function with recursive calls")
                return
            }
        }

        if (reference != null && RsInlineFunctionProcessor.checkIfLoopCondition(function, reference.element)) {
            errorHint(project, editor, "Cannot inline multiline function into \"while\" loop condition")
            return
        }

        if (function.block == null) {
            errorHint(project, editor, "Cannot inline an empty function")
            return
        }

        if (!isUnitTestMode) {
            val dialog = RsInlineFunctionDialog(function, reference as RsReference?, allowInlineThisOnly)
            dialog.show()
            if (!dialog.isOK) {
                val statusBar = WindowManager.getInstance().getStatusBar(function.project)
                statusBar?.info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
            }
        } else {
            val processor = RsInlineFunctionProcessor(
                project,
                function,
                reference as RsReference?,
                inlineThisOnly = false,
                removeDefinition = true
            )
            processor.run()
        }
    }

    override fun isEnabledForLanguage(l: Language?): Boolean = l == RsLanguage

    override fun canInlineElementInEditor(element: PsiElement, editor: Editor?): Boolean = canInlineElement(element)

    override fun canInlineElement(element: PsiElement): Boolean =
        element is RsFunction && element.navigationElement is RsFunction

    private fun errorHint(project: Project, editor: Editor, @Suppress("UnstableApiUsage") @DialogMessage message: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            message,
            RefactoringBundle.message("inline.method.title"),
            HELP_ID
        )
    }

    companion object {
        private const val HELP_ID: String = "refactoring.inlineMethod"
    }
}
