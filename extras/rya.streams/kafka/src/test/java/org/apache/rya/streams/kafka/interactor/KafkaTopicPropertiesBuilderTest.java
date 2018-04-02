/**
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
package org.apache.rya.streams.kafka.interactor;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.apache.kafka.common.config.ConfigException;
import org.junit.Test;

import kafka.log.LogConfig;

public class KafkaTopicPropertiesBuilderTest {

    @Test(expected=ConfigException.class)
    public void invalidProperty() {
        new KafkaTopicPropertiesBuilder()
            .setCleanupPolicy("invalid")
            .build();
    }

    @Test
    public void validProperty() {
        final Properties props = new KafkaTopicPropertiesBuilder()
            .setCleanupPolicy(LogConfig.Compact())
            .build();

        final Properties expected = new Properties();
        expected.setProperty(LogConfig.CleanupPolicyProp(), LogConfig.Compact());
        assertEquals(expected, props);
    }
}
