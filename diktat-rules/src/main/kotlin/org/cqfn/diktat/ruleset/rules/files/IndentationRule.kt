package org.cqfn.diktat.ruleset.rules.files

import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType.DOT_QUALIFIED_EXPRESSION
import com.pinterest.ktlint.core.ast.ElementType.ELSE
import com.pinterest.ktlint.core.ast.ElementType.FILE
import com.pinterest.ktlint.core.ast.ElementType.LBRACE
import com.pinterest.ktlint.core.ast.ElementType.LBRACKET
import com.pinterest.ktlint.core.ast.ElementType.LPAR
import com.pinterest.ktlint.core.ast.ElementType.RBRACE
import com.pinterest.ktlint.core.ast.ElementType.RBRACKET
import com.pinterest.ktlint.core.ast.ElementType.RPAR
import com.pinterest.ktlint.core.ast.ElementType.SAFE_ACCESS_EXPRESSION
import com.pinterest.ktlint.core.ast.ElementType.THEN
import com.pinterest.ktlint.core.ast.ElementType.WHITE_SPACE
import com.pinterest.ktlint.core.ast.visit
import org.cqfn.diktat.common.config.rules.RulesConfig
import org.cqfn.diktat.common.config.rules.getRuleConfig
import org.cqfn.diktat.ruleset.constants.Warnings.WRONG_INDENTATION
import org.cqfn.diktat.ruleset.utils.getAllLeafsWithSpecificType
import org.cqfn.diktat.ruleset.utils.getFileName
import org.cqfn.diktat.ruleset.utils.indentBy
import org.cqfn.diktat.ruleset.utils.indentation.ArrowInWhenChecker
import org.cqfn.diktat.ruleset.utils.indentation.AssignmentOperatorChecker
import org.cqfn.diktat.ruleset.utils.indentation.ConditionalsAndLoopsWithoutBracesChecker
import org.cqfn.diktat.ruleset.utils.indentation.CustomGettersAndSettersChecker
import org.cqfn.diktat.ruleset.utils.indentation.CustomIndentationChecker
import org.cqfn.diktat.ruleset.utils.indentation.DotCallChecker
import org.cqfn.diktat.ruleset.utils.indentation.ExpressionIndentationChecker
import org.cqfn.diktat.ruleset.utils.indentation.IndentationConfig
import org.cqfn.diktat.ruleset.utils.indentation.KDocIndentationChecker
import org.cqfn.diktat.ruleset.utils.indentation.SuperTypeListChecker
import org.cqfn.diktat.ruleset.utils.indentation.ValueParameterListChecker
import org.cqfn.diktat.ruleset.utils.leaveOnlyOneNewLine
import org.cqfn.diktat.ruleset.utils.log
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.util.containers.Stack
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

/**
 * Rule that checks indentation. The following general rules are checked:
 * 1. Only spaces should be used each indentation is equal to 4 spaces
 * 2. File should end with new line
 * Additionally, a set of CustomIndentationChecker objects checks all WHITE_SPACE node if they are exceptions from general rules.
 * @see CustomIndentationChecker
 */
class IndentationRule(private val configRules: List<RulesConfig>) : Rule("indentation") {
    companion object {
        const val INDENT_SIZE = 4
        private val increasingTokens = listOf(LPAR, LBRACE, LBRACKET)
        private val decreasingTokens = listOf(RPAR, RBRACE, RBRACKET)
        private val matchingTokens = increasingTokens.zip(decreasingTokens)
    }

    private val configuration: IndentationConfig by lazy {
        IndentationConfig(configRules.getRuleConfig(WRONG_INDENTATION)?.configuration ?: mapOf())
    }
    private lateinit var customIndentationCheckers: List<CustomIndentationChecker>

    private lateinit var emitWarn: ((offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit)
    private var isFixMode: Boolean = false
    private var fileName: String = ""

    override fun visit(node: ASTNode,
                       autoCorrect: Boolean,
                       emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit) {
        isFixMode = autoCorrect
        emitWarn = emit

        if (node.elementType == FILE) {
            fileName = node.getFileName()

            customIndentationCheckers = listOf(
                    ::AssignmentOperatorChecker,
                    ::ConditionalsAndLoopsWithoutBracesChecker,
                    ::SuperTypeListChecker,
                    ::ValueParameterListChecker,
                    ::ExpressionIndentationChecker,
                    ::DotCallChecker,
                    ::KDocIndentationChecker,
                    ::CustomGettersAndSettersChecker,
                    ::ArrowInWhenChecker
            ).map { it.invoke(configuration) }

            if (checkIsIndentedWithSpaces(node)) {
                checkIndentation(node)
            } else {
                log.warn("Not going to check indentation because there are tabs")
            }
            checkNewlineAtEnd(node)
        }
    }

    /**
     * This method warns if tabs are used in WHITE_SPACE nodes and substitutes them with spaces in fix mode
     * @return true if there are no tabs or all of them have been fixed, false otherwise
     */
    private fun checkIsIndentedWithSpaces(node: ASTNode): Boolean {
        val whiteSpaceNodes = mutableListOf<ASTNode>()
        node.getAllLeafsWithSpecificType(WHITE_SPACE, whiteSpaceNodes)
        whiteSpaceNodes
                .filter { it.textContains('\t') }
                .apply { if (isEmpty()) return true }
                .forEach {
                    WRONG_INDENTATION.warnAndFix(configRules, emitWarn, isFixMode, "tabs are not allowed for indentation", it.startOffset + it.text.indexOf('\t'), it) {
                        (it as LeafPsiElement).replaceWithText(it.text.replace("\t", " ".repeat(configuration.indentationSize)))
                    }
                }
        return isFixMode  // true if we changed all tabs to spaces
    }

    /**
     * Checks that file ends with exactly one empty line
     */
    private fun checkNewlineAtEnd(node: ASTNode) {
        if (configuration.newlineAtEnd) {
            val lastChild = node.lastChildNode
            val numBlankLinesAfter = lastChild.text.count { it == '\n' }
            if (lastChild.elementType != WHITE_SPACE || numBlankLinesAfter != 1) {
                val warnText = if (lastChild.elementType != WHITE_SPACE || numBlankLinesAfter == 0) "no newline" else "too many blank lines"
                WRONG_INDENTATION.warnAndFix(configRules, emitWarn, isFixMode, "$warnText at the end of file $fileName", node.startOffset + node.textLength, node) {
                    if (lastChild.elementType != WHITE_SPACE) {
                        node.addChild(PsiWhiteSpaceImpl("\n"), null)
                    } else {
                        lastChild.leaveOnlyOneNewLine()
                    }
                }
            }
        }
    }

    /**
     * Traverses the tree, keeping track of regular and exceptional indentations
     */
    private fun checkIndentation(node: ASTNode) {
        val context = IndentContext(configuration)
        node.visit { astNode ->
            context.checkAndReset(astNode)
            if (astNode.elementType in increasingTokens) {
                context.storeIncrementingToken(astNode.elementType)
            } else if (astNode.elementType in decreasingTokens && !astNode.treePrev.let { it.elementType == WHITE_SPACE && it.textContains('\n') }) {
                // if decreasing token is after WHITE_SPACE with \n, indents are corrected in visitWhiteSpace method
                context.dec(astNode.elementType)
            } else if (astNode.elementType == WHITE_SPACE && astNode.textContains('\n') && astNode.treeNext != null) {
                // we check only WHITE_SPACE nodes with newlines, other than the last line in file; correctness of newlines should be checked elsewhere
                visitWhiteSpace(astNode, context)
            }
        }
    }

    @Suppress("ForbiddenComment")
    private fun visitWhiteSpace(astNode: ASTNode, context: IndentContext) {
        context.maybeIncrement()

        val whiteSpace = astNode.psi as PsiWhiteSpace
        if (astNode.treeNext.elementType in decreasingTokens) {
            // if newline is followed by closing token, it should already be indented less
            context.dec(astNode.treeNext.elementType)
        }

        val indentError = IndentationError(context.indent(), astNode.text.lastIndent())

        val checkResult = customIndentationCheckers.firstNotNullResult {
            it.checkNode(whiteSpace, indentError)
        }

        val expectedIndent = checkResult?.expectedIndent ?: indentError.expected
        if (checkResult?.adjustNext == true) {
            val exceptionInitiatorNode = astNode.getExceptionalIndentInitiator()
            context.addException(exceptionInitiatorNode, expectedIndent - indentError.expected, checkResult.includeLastChild)
        }
        if (checkResult?.isCorrect != true && expectedIndent != indentError.actual) {
            WRONG_INDENTATION.warnAndFix(configRules, emitWarn, isFixMode, "expected $expectedIndent but was ${indentError.actual}",
                    whiteSpace.startOffset + whiteSpace.text.lastIndexOf('\n') + 1, whiteSpace.node) {
                whiteSpace.node.indentBy(expectedIndent)
            }
        }
    }

    private fun ASTNode.getExceptionalIndentInitiator() = treeParent.let { parent ->
        when (parent.psi) {
            // fixme: custom logic for determining exceptional indent initiator, should be moved elsewhere
            is KtDotQualifiedExpression ->
                // get the topmost expression to keep extended indent for the whole chain of dot call expressions
                parents().takeWhile { it.elementType == DOT_QUALIFIED_EXPRESSION || it.elementType == SAFE_ACCESS_EXPRESSION }.last()
            is KtIfExpression -> parent.findChildByType(THEN) ?: parent.findChildByType(ELSE) ?: parent
            is KtLoopExpression -> (parent.psi as KtLoopExpression).body?.node ?: parent
            else -> parent
        }
    }

    /**
     * Class that contains state needed to calculate indent and keep track of exceptional indents.
     * Tokens from [increasingTokens] are stored in stack [activeTokens]. When [WHITE_SPACE] with line break is encountered,
     * if stack is not empty, indentation is increased. When token from [decreasingTokens] is encountered, it's counterpart is removed
     * from stack. If there has been a [WHITE_SPACE] with line break between them, indentation is decreased.
     */
    private class IndentContext(private val config: IndentationConfig) {
        private var regularIndent = 0
        private val exceptionalIndents = mutableListOf<ExceptionalIndent>()
        private val activeTokens = Stack<IElementType>()

        fun storeIncrementingToken(token: IElementType) = token
                .also { require(it in increasingTokens) { "Only tokens that increase indentation should be passed to this method" } }
                .let(activeTokens::push)

        fun maybeIncrement() {
            if (activeTokens.isNotEmpty() && activeTokens.peek() != WHITE_SPACE) {
                regularIndent += config.indentationSize
                activeTokens.push(WHITE_SPACE)
            }
        }

        fun dec(token: IElementType) {
            if (activeTokens.peek() == WHITE_SPACE) {
                while (activeTokens.peek() == WHITE_SPACE) activeTokens.pop()
                regularIndent -= config.indentationSize
            }
            if (activeTokens.isNotEmpty() && activeTokens.peek() == matchingTokens.find { it.second == token }?.first) {
                activeTokens.pop()
            }
        }

        fun indent() = regularIndent + exceptionalIndents.sumBy { it.indent }

        fun addException(initiator: ASTNode, indent: Int, includeLastChild: Boolean) =
                exceptionalIndents.add(ExceptionalIndent(initiator, indent, includeLastChild))

        fun checkAndReset(astNode: ASTNode) = exceptionalIndents.retainAll { it.isActive(astNode) }

        private data class ExceptionalIndent(val initiator: ASTNode, val indent: Int, val includeLastChild: Boolean = true) {
            /**
             * Checks whether this exceptional indent is still active. This is a hypotheses that exceptional indentation will end
             * outside of node where it appeared, e.g. when an expression after assignment operator is over.
             */
            fun isActive(currentNode: ASTNode): Boolean = currentNode.psi.parentsWithSelf.any { it.node == initiator } &&
                    (includeLastChild || currentNode.treeNext != initiator.lastChildNode)
        }
    }
}

internal data class IndentationError(val expected: Int, val actual: Int)

internal fun String.lastIndent() = substringAfterLast('\n').count { it == ' ' }
