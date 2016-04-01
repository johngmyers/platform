/*
 * Copyright 2014 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.LoggingConfiguration;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.testing.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static com.google.common.io.Files.createTempDir;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertTrue;

public class TestHealthRegistrationLogger
{
    private static final JsonCodec<Object> OBJECT_JSON_CODEC = jsonCodec(Object.class);
    private File tempDir;
    private HealthBeanRegistry healthBeanRegistry;
    private LoggingConfiguration loggingConfiguration;
    private HealthRegistrationLogger healthRegistrationLogger;
    private HealthExporter healthExporter;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        tempDir = createTempDir();
        healthBeanRegistry = new HealthBeanRegistry();
        loggingConfiguration = new LoggingConfiguration();
        healthRegistrationLogger = new HealthRegistrationLogger(healthBeanRegistry, loggingConfiguration, new NodeInfo("testing"));
        healthExporter = new HealthExporter(healthBeanRegistry);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
            throws IOException
    {
        FileUtils.deleteRecursively(tempDir);
    }

    @Test
    public void testLogHealthRegistration()
            throws Exception
    {
        healthExporter.export(null, new TestHealth1());
        healthExporter.export("two", new TestHealth2());
        loggingConfiguration.setLogPath(new File(tempDir, "launcher.log").getPath());

        healthRegistrationLogger.logHealthRegistrations();

        File file = new File(tempDir, "healthchecks.json");
        assertTrue(file.exists(), file + " exists");

        byte[] bytes = Files.readAllBytes(file.toPath());
        Object json = OBJECT_JSON_CODEC.fromJson(bytes);

        assertInstanceOf(json, Map.class);
        assertEquals(((Map<?, ?>)json).keySet(), ImmutableSet.of("checks"));

        assertEqualsIgnoreOrder(((Map<?, List<?>>) json).get("checks"), ImmutableList.<Object>of(
                ImmutableMap.of("description", "test-application Check one"),
                ImmutableMap.of("description", "test-application Check two"),
                ImmutableMap.of("description", "test-application Check three (two)")
        ));
    }

    @Test
    public void testNoLogPath()
            throws Exception
    {
        healthExporter.export(null, new TestHealth1());
        healthExporter.export("two", new TestHealth2());
        loggingConfiguration.setLogPath(null);

        healthRegistrationLogger.logHealthRegistrations();
    }

    private static class TestHealth1
    {
        @HealthCheck("Check one")
        public String getCheckOne()
        {
            return "Failed check one";
        }

        @HealthCheck("Check two")
        private Object getCheckTwo()
        {
            return null;
        }
    }

    private static class TestHealth2
    {
        @HealthCheck("Check three")
        private String getCheckThree()
        {
            return "Failed check three";
        }
    }
}
