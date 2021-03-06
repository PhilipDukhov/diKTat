package org.cqfn.diktat.ruleset.chapter3.spaces

import generated.WarningNames
import org.cqfn.diktat.ruleset.rules.files.WhiteSpaceRule
import org.cqfn.diktat.util.FixTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class WhiteSpaceRuleFixTest : FixTestBase("test/paragraph3/spaces", ::WhiteSpaceRule) {
    @Test
    @Tag(WarningNames.WRONG_WHITESPACE)
    fun `should keep single whitespace between keyword and opening parentheses`() {
        fixAndCompare("WhiteSpaceBeforeLParExpected.kt", "WhiteSpaceBeforeLParTest.kt")
    }

    @Test
    @Tag(WarningNames.WRONG_WHITESPACE)
    fun `should keep single whitespace between keyword and opening brace`() {
        fixAndCompare("LBraceAfterKeywordExpected.kt", "LBraceAfterKeywordTest.kt")
    }

    @Test
    @Tag(WarningNames.WRONG_WHITESPACE)
    fun `should remove spaces between ( and { when lambda is used as an argument`() {
        fixAndCompare("LambdaAsArgumentExpected.kt", "LambdaAsArgumentTest.kt")
    }

    @Test
    @Tag(WarningNames.WRONG_WHITESPACE)
    fun `should keep single whitespace before any other opening brace`() {
        fixAndCompare("LbraceExpected.kt", "LbraceTest.kt")
    }

    @Test
    @Tag(WarningNames.WRONG_WHITESPACE)
    fun `should surround binary operators with spaces`() {
        fixAndCompare("BinaryOpExpected.kt", "BinaryOpTest.kt")
    }

    @Test
    @Tag(WarningNames.WRONG_WHITESPACE)
    fun `should trim spaces in the end of line`() {
        fixAndCompare("EolSpacesExpected.kt", "EolSpacesTest.kt")
    }

    @Test
    @Tag(WarningNames.WRONG_WHITESPACE)
    fun `should fix space in annotation`() {
        fixAndCompare("AnnotationExpected.kt", "AnnotationTest.kt")
    }

    @Test
    @Tag(WarningNames.WRONG_WHITESPACE)
    fun `should add spaes on both sides of equals`() {
        fixAndCompare("EqualsExpected.kt", "EqualsTest.kt")
    }
}
