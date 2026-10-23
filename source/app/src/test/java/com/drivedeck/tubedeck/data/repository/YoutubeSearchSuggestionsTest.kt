package com.drivedeck.tubedeck.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubeSearchSuggestionsTest {
    @Test fun parsesGoogleArrayWithoutInterpretingMetadataAsTerms() {
        assertEquals(listOf("pakistan news", "pakistan weather"),
            YoutubeSearchSuggestions.parse("""["pak",["pakistan news","pakistan weather"],[],{"metadata":1}]"""))
    }
    @Test fun handlesNestedSuggestionRowsAndEmptyResponse() {
        assertEquals(listOf("اردو خبریں"), YoutubeSearchSuggestions.parse("""["اردو",[["اردو خبریں",0]]]"""))
        assertEquals(emptyList<String>(), YoutubeSearchSuggestions.parse("""["none",[]]"""))
    }
}
