package com.widdit.nowplaying.entity;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackSerializationTest {
    @Test
    void semanticProvenanceIsExcludedFromBothApiSerializers() throws Exception {
        Track track = Track.builder().id("123").title("Song").semanticMatchConfirmed(true).build();
        assertFalse(new ObjectMapper().writeValueAsString(track).contains("semanticMatchConfirmed"));
        assertFalse(JSON.toJSONString(track).contains("semanticMatchConfirmed"));
    }

    @Test
    void apiInputCannotClaimSemanticConfirmation() throws Exception {
        String json = "{\"id\":\"123\",\"semanticMatchConfirmed\":true}";
        assertFalse(new ObjectMapper().readValue(json, Track.class).isSemanticMatchConfirmed());
        assertFalse(JSON.parseObject(json, Track.class).isSemanticMatchConfirmed());
    }
}
