package org.cqfn.diktat.ruleset.rules

import com.pinterest.ktlint.core.Rule
import org.cqfn.diktat.ruleset.constants.EmitType
import com.pinterest.ktlint.core.ast.ElementType
import com.pinterest.ktlint.core.ast.ElementType.BINARY_EXPRESSION
import com.pinterest.ktlint.core.ast.ElementType.CONDITION
import com.pinterest.ktlint.core.ast.ElementType.IF
import com.pinterest.ktlint.core.ast.ElementType.IF_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.NULL
import com.pinterest.ktlint.core.ast.ElementType.REFERENCE_EXPRESSION
import com.pinterest.ktlint.core.ast.ElementType.VALUE_ARGUMENT
import com.pinterest.ktlint.core.ast.ElementType.VALUE_ARGUMENT_LIST
import com.pinterest.ktlint.core.ast.parent
import org.cqfn.diktat.common.config.rules.RulesConfig
import org.cqfn.diktat.ruleset.constants.Warnings.AVOID_NULL_CHECKS
import org.cqfn.diktat.ruleset.utils.*
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtIfExpression

/**
 * This rule check and fixes explicit null checks (explicit comparison with `null`)
 * There are several code-structures that can be used in Kotlin to avoid null-checks. For example: `?:`,  `.let {}`, `.also {}`, e.t.c
 */
class NullChecksRule(private val configRules: List<RulesConfig>) : Rule("null-checks") {
    private var isFixMode: Boolean = false
    private lateinit var emitWarn: EmitType

    override fun visit(node: ASTNode,
                       autoCorrect: Boolean,
                       emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit) {
        emitWarn = emit
        isFixMode = autoCorrect

        if (node.elementType == CONDITION) {
            node.parent(IF)?.let {
                // excluding complex cases with else-if statements, because they look better with explicit null-check
                if (!isComplexIfStatement(it)) {
                    // this can be autofixed as the condition stays in simple if-statement
                    conditionInIfStatement(node)
                }
            }
        }

        if (node.elementType == BINARY_EXPRESSION) {
            // `condition` case is already checked above, so no need to check it once again
            node.parent(CONDITION) ?: run {
                // only warning here, because autofix in other statements (like) lambda (or value) can break the code
                nullCheckInOtherStatements(node)
            }
        }
    }

    /**
     * checks that if-statement is a complex condition
     * You can name a statement - "complex if-statement" if it has other if in the else branch (else-if structure)
     */
    private fun isComplexIfStatement(parentIf: ASTNode): Boolean {
        val parentIfPsi = parentIf.psi
        require(parentIfPsi is KtIfExpression)
        return (parentIfPsi.`else`?.node?.firstChildNode?.elementType == IF_KEYWORD)
    }

    private fun conditionInIfStatement(node: ASTNode) {
        node.findAllNodesWithSpecificType(BINARY_EXPRESSION).forEach { binaryExprNode ->
            val condition = (binaryExprNode.psi as KtBinaryExpression)
            if (isNullCheckBinaryExpession(condition)) {
                when (condition.operationToken) {
                    // `==` and `===` comparison can be fixed with `?:` operator
                    ElementType.EQEQ, ElementType.EQEQEQ ->
                        warnAndFixOnNullCheck(condition, true,
                                "use '.let/.also/?:/e.t.c' instead of ${condition.text}") {}
                    // `!==` and `!==` comparison can be fixed with `.let/also` operators
                    ElementType.EXCLEQ, ElementType.EXCLEQEQEQ ->
                        warnAndFixOnNullCheck(condition, true,
                                "use '.let/.also/?:/e.t.c' instead of ${condition.text}") {}
                    else -> return
                }
            }
        }
    }

    private fun nullCheckInOtherStatements(binaryExprNode: ASTNode) {
        val condition = (binaryExprNode.psi as KtBinaryExpression)
        if (isNullCheckBinaryExpession(condition)) {

            // require(a != null) is incorrect, Kotlin has special method: `requireNotNull` - need to use it instead
            // hierarchy is the following:
            //              require(a != null)
            //                 /            \
            //      REFERENCE_EXPRESSION    VALUE_ARGUMENT_LIST
            //                |                       |
            //          IDENTIFIER(require)     VALUE_ARGUMENT
            val parent = binaryExprNode.treeParent
            if (parent != null && parent.elementType == VALUE_ARGUMENT) {
                val grandParent = parent.treeParent
                if (grandParent != null && grandParent.elementType == VALUE_ARGUMENT_LIST && isRequireFun(grandParent.treePrev)) {
                    if (listOf(ElementType.EXCLEQ, ElementType.EXCLEQEQEQ).contains(condition.operationToken)) {
                        warnAndFixOnNullCheck(
                                condition,
                                false,
                                "use 'requireNotNull' instead of require(${condition.text})"
                        ) {}
                    }
                }
            }
        }
    }

    @Suppress("UnsafeCallOnNullableType")
    private fun isNullCheckBinaryExpession(condition: KtBinaryExpression): Boolean =
            // check that binary expression has `null` as right or left operand
            setOf(condition.right, condition.left).map { it!!.node.elementType }.contains(NULL) &&
                    // checks that it is the comparison condition
                    setOf(ElementType.EQEQ, ElementType.EQEQEQ, ElementType.EXCLEQ, ElementType.EXCLEQEQEQ)
                            .contains(condition.operationToken) &&
                    // no need to raise warning or fix null checks in complex expressions
                    !condition.isComplexCondition()

    /**
     * checks if condition is a complex expression. For example:
     * (a == 5) - is not a complex condition, but (a == 5 && b != 6) is a complex condition
     */
    private fun KtBinaryExpression.isComplexCondition(): Boolean {
        // KtBinaryExpression is complex if it has a parent that is also a binary expression
        return this.parent is KtBinaryExpression
    }

    private fun warnAndFixOnNullCheck(condition: KtBinaryExpression, canBeAutoFixed: Boolean, freeText: String, autofix: () -> Unit) {
        AVOID_NULL_CHECKS.warnAndFix(
                configRules,
                emitWarn,
                isFixMode,
                freeText,
                condition.node.startOffset,
                condition.node,
                canBeAutoFixed,
        ) {
            autofix()
        }
    }

    private fun isRequireFun(referenceExpression: ASTNode) =
            referenceExpression.elementType == REFERENCE_EXPRESSION && referenceExpression.firstChildNode.text == "require"

}
