/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.StubTree
import com.intellij.psi.stubs.StubTreeBuilder
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.externalizer.StringCollectionExternalizer
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.MacroExpansionStubsProvider
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.ownerBySyntaxOnly
import org.rust.lang.core.resolve.RsCachedTypeAlias
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsTypeAliasStub
import org.rust.lang.core.types.TyFingerprint
import org.rust.openapiext.toPsiFile

class RsAliasIndex : FileBasedIndexExtension<TyFingerprint, List<String>>() {
    override fun getName(): ID<TyFingerprint, List<String>> = KEY

    override fun getIndexer() = object : DataIndexer<TyFingerprint, List<String>, FileContent> {
        override fun map(inputData: FileContent): Map<TyFingerprint, List<String>> {
            val stubTree = getStubTree(inputData) ?: return emptyMap()
            val map = hashMapOf<TyFingerprint, MutableList<String>>()
            for (stub in stubTree.plainList) {
                if (stub is RsTypeAliasStub) {
                    val psi = stub.psi
                    if (psi.ownerBySyntaxOnly !is RsAbstractableOwner.Impl) {
                        val aliasedName = stub.name ?: continue
                        val typeRef = psi.typeReference ?: continue
                        for (tyf in TyFingerprint.create(typeRef, emptyList())) {
                            map.getOrPut(tyf) { mutableListOf() } += aliasedName
                        }
                    }
                }
            }
            return map
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<TyFingerprint> = TyFingerprint.KeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<List<String>> = StringCollectionExternalizer.STRING_LIST_EXTERNALIZER

    override fun getVersion(): Int = RsFileStub.Type.stubVersion

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(RsFileType)

    override fun dependsOnFileContent(): Boolean = true

    companion object {
        fun findPotentialAliases(
            project: Project,
            tyf: TyFingerprint,
        ): List<RsCachedTypeAlias> {
            val result = hashMapOf<String, RsCachedTypeAlias>()
            FileBasedIndex.getInstance().processValues(
                KEY,
                tyf,
                null,
                { file, value ->
                    val psi = file.toPsiFile(project) as? RsFile
                    if (psi != null) {
                        val crates = psi.crates
                        if (crates.isNotEmpty()) {
                            for (name in value) {
                                result.getOrPut(name) { RsCachedTypeAlias(name) }.containingCrates += crates
                            }
                        }
                    }
                    true
                },
                RsWithMacrosProjectScope(project)
            )
            return result.values.toList()
        }

        private fun getStubTree(inputData: FileContent): StubTree? {
            val rootStub = MacroExpansionStubsProvider.findStubForMacroExpansionFile(inputData)
                ?: StubTreeBuilder.buildStubTree(inputData)
            return if (rootStub is RsFileStub) StubTree(rootStub) else null
        }

        private val KEY: ID<TyFingerprint, List<String>> = ID.create("org.rust.lang.core.resolve.indexes.RsAliasIndex")
    }
}
