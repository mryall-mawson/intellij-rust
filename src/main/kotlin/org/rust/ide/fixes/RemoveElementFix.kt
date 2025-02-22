/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

open class RemoveElementFix(
    element: PsiElement,
    private val removingElementName: String = "`${element.text}`"
) : RsQuickFixBase<PsiElement>(element) {
    override fun getFamilyName(): String = "Remove"
    override fun getText(): String = "Remove $removingElementName"
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        element.delete()
    }
}
