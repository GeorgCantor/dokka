package org.jetbrains.dokka.analysis.java.parsers

import com.intellij.psi.PsiNamedElement
import org.jetbrains.dokka.analysis.java.DocCommentFinder
import org.jetbrains.dokka.analysis.java.DocCommentParser
import org.jetbrains.dokka.model.doc.DocumentationNode

fun interface JavaDocumentationParser {
    fun parseDocumentation(element: PsiNamedElement): DocumentationNode
}

class JavadocParser(
    private val docCommentParsers: List<DocCommentParser>,
    private val docCommentFinder: DocCommentFinder
) : JavaDocumentationParser {

    override fun parseDocumentation(element: PsiNamedElement): DocumentationNode {
        val comment = docCommentFinder.findClosestToElement(element) ?: return DocumentationNode(emptyList())
        return docCommentParsers
            .first { it.canParse(comment) }
            .parse(comment, element)
    }
}
