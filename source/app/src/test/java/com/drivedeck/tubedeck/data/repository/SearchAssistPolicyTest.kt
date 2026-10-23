package com.drivedeck.tubedeck.data.repository

import org.junit.Assert.*
import org.junit.Test

class SearchAssistPolicyTest {
    @Test fun repeatedSearchMovesToFrontWithoutCaseDuplicate() {
        assertEquals(listOf("Pakistan news", "music"),
            SearchAssistPolicy.record("  Pakistan   news  ", listOf("music", "PAKISTAN NEWS")))
    }

    @Test fun historyIsBoundedAndSupportsSingleCharacterAndUrdu() {
        val old = (1..25).map { "query $it" }
        assertEquals(20, SearchAssistPolicy.record("اردو خبریں", old).size)
        assertEquals("a", SearchAssistPolicy.record("a", old).first())
    }

    @Test fun urlsAndEmptyInputsAreNotStoredAsSearches() {
        val history = listOf("news")
        assertEquals(history, SearchAssistPolicy.record("https://youtube.com/watch?v=abc", history))
        assertEquals(history, SearchAssistPolicy.record("  ", history))
    }

    @Test fun matchingHistoryWorksOfflineAndDuplicatesAreRemovedFromSuggestions() {
        assertEquals(listOf("Pakistan news", "Pakistan weather"),
            SearchAssistPolicy.merge("pak", listOf("Pakistan news", "music"),
                listOf("PAKISTAN NEWS", "Pakistan weather")))
        assertEquals(listOf("اردو خبریں"), SearchAssistPolicy.merge("اردو", listOf("اردو خبریں"), emptyList()))
    }
}
