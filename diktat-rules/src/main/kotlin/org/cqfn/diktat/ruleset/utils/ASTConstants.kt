package org.cqfn.diktat.ruleset.utils

import com.pinterest.ktlint.core.ast.ElementType
import com.pinterest.ktlint.core.ast.ElementType.BLOCK_COMMENT
import com.pinterest.ktlint.core.ast.ElementType.EOL_COMMENT
import com.pinterest.ktlint.core.ast.ElementType.KDOC
import com.pinterest.ktlint.core.ast.ElementType.LBRACE
import com.pinterest.ktlint.core.ast.ElementType.RBRACE
import com.pinterest.ktlint.core.ast.ElementType.SEMICOLON
import com.pinterest.ktlint.core.ast.ElementType.WHITE_SPACE

/**
 * List of standard methods which do not need mandatory documentation
 */
internal val STANDARD_METHODS = listOf("main", "equals", "hashCode", "toString", "clone", "finalize")

internal const val GET_PREFIX = "get"
internal const val SET_PREFIX = "set"

/**
 * List of element types present in empty code block `{ }`
 */
val emptyBlockList = listOf(LBRACE, WHITE_SPACE, SEMICOLON, RBRACE)

val COMMENT_TYPE = listOf(BLOCK_COMMENT, EOL_COMMENT, KDOC)

internal const val EMPTY_BLOCK_TEXT = "{}"

enum class StandardPlatforms(val packages: List<String>) {
    ANDROID(listOf("android", "androidx", "com.android")),
    JAVA(listOf("java", "javax")),
    KOTLIN(listOf("kotlin", "kotlinx")),
    ;
}
