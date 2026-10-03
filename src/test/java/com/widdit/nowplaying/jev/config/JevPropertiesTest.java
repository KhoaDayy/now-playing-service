package com.widdit.nowplaying.jev.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JevPropertiesTest {

    @Test
    void explicitDisabledConfigurationIsPreservedWhenApiKeyExists() {
        JevProperties properties = new JevProperties();
        properties.setApiKey("test-key");
        properties.setEnabled(false);

        properties.init();

        assertFalse(properties.isEnabled());
        assertFalse(properties.isOperational());
    }

    @Test
    void explicitDisableSurvivesSystemPropertyKeyResolution() {
        String priorKey = System.getProperty("jev.api-key");
        try {
            System.setProperty("jev.api-key", "system-test-key");
            JevProperties properties = new JevProperties();
            properties.setApiKey("");
            properties.setEnabled(false);

            properties.init();

            assertFalse(properties.isEnabled());
            assertFalse(properties.isOperational());
            assertNotNull(properties.getApiKey());
        } finally {
            if (priorKey == null) {
                System.clearProperty("jev.api-key");
            } else {
                System.setProperty("jev.api-key", priorKey);
            }
        }
    }

    @Test
    void invalidTimeoutAndMissingDefaultsAreNormalized() {
        JevProperties properties = new JevProperties();
        properties.setTimeoutMs(-1);
        properties.setApiUrl(" ");
        properties.setModel(null);

        properties.init();

        assertTrue(properties.getTimeoutMs() > 0);
        assertEquals("https://api.typesafe.ai/v1/systemone", properties.getApiUrl());
        assertEquals("jev-latest", properties.getModel());
    }

    @Test
    void configuredModelAndPositiveTimeoutAreRespected() {
        JevProperties properties = new JevProperties();
        properties.setModel("jev-custom-model");
        properties.setTimeoutMs(1234);

        properties.init();

        assertEquals("jev-custom-model", properties.getModel());
        assertEquals(1234, properties.getTimeoutMs());
    }

    @Test
    void diagnosticStringNeverIncludesApiKey() {
        JevProperties properties = new JevProperties();
        properties.setApiKey("test-key-that-must-stay-private");

        assertFalse(properties.toString().contains("test-key-that-must-stay-private"));
    }
}
