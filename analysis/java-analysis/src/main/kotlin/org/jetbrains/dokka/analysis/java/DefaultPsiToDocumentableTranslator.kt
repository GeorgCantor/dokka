package org.jetbrains.dokka.analysis.java

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifierListOwner
import kotlinx.coroutines.coroutineScope
import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.JavaVisibility
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.sources.AsyncSourceToDocumentableTranslator
import org.jetbrains.dokka.utilities.parallelMap
import org.jetbrains.dokka.utilities.parallelMapNotNull

class DefaultPsiToDocumentableTranslator(
    val javaAnalysisHelper: JavaAnalysisHelper,
) : AsyncSourceToDocumentableTranslator {

    override suspend fun invokeSuspending(sourceSet: DokkaSourceSet, context: DokkaContext): DModule {
        return coroutineScope {
            val project = javaAnalysisHelper.extractProject(sourceSet, context)
            val sourceRoots = javaAnalysisHelper.extractSourceRoots(sourceSet, context)
            val localFileSystem = VirtualFileManager.getInstance().getFileSystem("file")

            val psiFiles = sourceRoots.parallelMap { sourceRoot ->
                sourceRoot.absoluteFile.walkTopDown().mapNotNull {
                    localFileSystem.findFileByPath(it.path)?.let { vFile ->
                        PsiManager.getInstance(project).findFile(vFile) as? PsiJavaFile
                    }
                }.toList()
            }.flatten()

            val docParser = javaAnalysisHelper.createPsiParser(sourceSet, context)

            DModule(
                name = context.configuration.moduleName,
                packages = psiFiles.parallelMapNotNull { it }.groupBy { it.packageName }.toList()
                    .parallelMap { (packageName: String, psiFiles: List<PsiJavaFile>) ->
                        docParser.parsePackage(packageName, psiFiles)
                    },
                documentation = emptyMap(),
                expectPresentInSet = null,
                sourceSets = setOf(sourceSet)
            )
        }
    }
}

fun PsiModifierListOwner.getVisibility() = modifierList?.let {
    val ml = it.children.toList()
    when {
        ml.any { it.text == PsiKeyword.PUBLIC } || it.hasModifierProperty("public") -> JavaVisibility.Public
        ml.any { it.text == PsiKeyword.PROTECTED } || it.hasModifierProperty("protected") -> JavaVisibility.Protected
        ml.any { it.text == PsiKeyword.PRIVATE } || it.hasModifierProperty("private") -> JavaVisibility.Private
        else -> JavaVisibility.Default
    }
} ?: JavaVisibility.Default
