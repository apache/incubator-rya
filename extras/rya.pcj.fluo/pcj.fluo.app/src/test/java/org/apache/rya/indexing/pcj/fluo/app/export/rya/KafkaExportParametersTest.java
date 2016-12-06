/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.rya.indexing.pcj.fluo.app.export.rya;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

/**
 * Tests the methods of {@link KafkaExportParameters}.
 */
public class KafkaExportParametersTest {

    @Test
    public void writeParams() {
        final Map<String, String> params = new HashMap<>();

        // Load some values into the params using the wrapper.
        final KafkaExportParameters kafkaParams = new KafkaExportParameters(params);
        kafkaParams.setExportToKafka(true);

        // Ensure the params map has the expected values.
        final Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put(KafkaExportParameters.CONF_EXPORT_TO_KAFKA, "true");
        assertTrue(kafkaParams.isExportToKafka());
        assertEquals(expectedParams, params);

        // now go the other way.
        expectedParams.put(KafkaExportParameters.CONF_EXPORT_TO_KAFKA, "false");
        kafkaParams.setExportToKafka(false);
        assertFalse(kafkaParams.isExportToKafka());
        assertEquals(expectedParams, params);
    }
    @Test
    public void writeParamsProps() {
        final String KEY1 = "key1";
        final String VALUE1FIRST = "value1-preserve-this";
        final String VALUE1SECOND = "value1prop";
        final String KEY2 = "歌古事学週文原問業間革社。"; // http://generator.lorem-ipsum.info/_chinese
        final String VALUE2 = "良治鮮猿性社費著併病極験。";

        final Map<String, String> params = new HashMap<>();
        // Make sure export key1 is kept seperate from producer config key1
        params.put(KEY1, VALUE1FIRST);
        final KafkaExportParameters kafkaParams = new KafkaExportParameters(params);
        // Load some values into the properties using the wrapper.
        Properties props = new Properties();
        props.put(KEY1, VALUE1SECOND);
        props.put(KEY2, VALUE2);
        kafkaParams.setProducerConfig(props);
        Properties propsAfter = kafkaParams.getProducerConfig();
        assertEquals(props, propsAfter);
        assertEquals(params, params);
        assertEquals("Should not change identical parameters key", params.get(KEY1), VALUE1FIRST);
        assertEquals("Props should not have params's key", propsAfter.get(KEY1), VALUE1SECOND);
        assertNull("Should not have props key", params.get(KEY2));
    }

    @Test
    public void notConfigured() {
        final Map<String, String> params = new HashMap<>();

        // Ensure an unconfigured parameters map will say kafka export is disabled.
        final KafkaExportParameters kafkaParams = new KafkaExportParameters(params);
        assertFalse(kafkaParams.isExportToKafka());
    }

    @Test
    public void testKafkaResultExporterFactory() {
        KafkaResultExporterFactory factory = new KafkaResultExporterFactory();
        assertNotNull(factory);
        // KafkaExportParameters params = new KafkaExportParameters(new HashMap<String, String>());
        // factory.build( need context );
    }
}