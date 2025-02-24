/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.log;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestLogging
{
    private Path tempPath;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempPath = Files.createTempDirectory(null);
    }

    @AfterMethod
    public void tearDown()
            throws IOException
    {
        deleteRecursively(tempPath, ALLOW_INSECURE);
    }

    @Test
    public void testRecoverTempFiles()
            throws IOException
    {
        LoggingConfiguration configuration = new LoggingConfiguration();
        configuration.setLogPath(tempPath.resolve("launcher.log").toString());

        Path logPath1 = tempPath.resolve("test1.log");
        Files.write(logPath1, new byte[]{});
        Path logPath2 = tempPath.resolve("test2.log");
        Files.write(logPath2, new byte[]{});
        Path tempLogPath1 = tempPath.resolve("temp1.tmp");
        Files.write(tempLogPath1, new byte[]{});
        Path tempLogPath2 = tempPath.resolve("temp2.tmp");
        Files.write(tempLogPath2, new byte[]{});

        Logging logging = Logging.initialize();
        logging.configure(configuration);

        assertTrue(Files.exists(logPath1));
        assertTrue(Files.exists(logPath2));
        assertFalse(Files.exists(tempLogPath1));
        assertFalse(Files.exists(tempLogPath2));

        assertTrue(Files.exists(tempPath.resolve("temp1.log")));
        assertTrue(Files.exists(tempPath.resolve("temp2.log")));
    }

    @Test
    public void testPropagatesLevels()
    {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testPropagatesLevels");

        logging.setLevel("testPropagatesLevels", Level.ERROR);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevels", Level.WARN);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevels", Level.INFO);
        assertFalse(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevels", Level.DEBUG);
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevels", Level.TRACE);
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevels", Level.ALL);
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());
    }

    @Test
    public void testPropagatesLevelsHierarchical()
    {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testPropagatesLevelsHierarchical.child");

        logging.setLevel("testPropagatesLevelsHierarchical", Level.ERROR);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevelsHierarchical", Level.WARN);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevelsHierarchical", Level.INFO);
        assertFalse(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevelsHierarchical", Level.DEBUG);
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevelsHierarchical", Level.TRACE);
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevelsHierarchical", Level.ALL);
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());
    }

    @Test
    public void testChildLevelOverridesParent()
    {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testChildLevelOverridesParent.child");

        logging.setLevel("testChildLevelOverridesParent", Level.DEBUG);
        logging.setLevel("testChildLevelOverridesParent.child", Level.ERROR);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());
    }

    @Test
    public void testAddLogTester()
    {
        Logging.initialize();
        List<String> logRecords = new ArrayList<>();
        Logging.addLogTester(TestAddLogTester.class, (level, message, thrown) -> {
            assertEquals(level, Level.INFO);
            assertFalse(thrown.isPresent());
            logRecords.add(message);
        });
        Logger.get(TestAddLogTester.class).info("test log line");
        assertEquals(logRecords.size(), 1);
        assertEquals(logRecords.get(0), "test log line");
    }

    private static class TestAddLogTester
    {
    }

    @Test
    public void testAddLogTesterThrown()
    {
        Logging.initialize();
        List<String> logRecords = new ArrayList<>();
        Exception testingException = new Exception();
        Logging.addLogTester(TestAddLogTesterThrown.class, (level, message, thrown) -> {
            assertEquals(level, Level.WARN);
            assertEquals(thrown.orElseThrow(), testingException);
            logRecords.add(message);
        });
        Logger.get(TestAddLogTesterThrown.class).warn(testingException, "test log line");
        assertEquals(logRecords.size(), 1);
        assertEquals(logRecords.get(0), "test log line");
    }

    private static class TestAddLogTesterThrown
    {
    }

    @Test
    public void testLoggingOutputStream()
    {
        Logging.initialize();
        List<String> logRecords = new ArrayList<>();
        Logging.addLogTester("stdout", (level, message, thrown) -> {
            assertEquals(level, Level.INFO);
            assertFalse(thrown.isPresent());
            logRecords.add(message);
        });
        System.out.println("test log line %");
        assertEquals(logRecords.size(), 1);
        assertEquals(logRecords.get(0), "test log line %");
    }

    @Test
    public void testResetLogHandlers()
    {
        Logging.initialize();
        Logging.addLogTester(TestResetLogHandlers.class, (level, message, thrown) -> {
            fail("Unexpected call to publish");
        });
        Logging.resetLogTesters();
        Logger.get(TestResetLogHandlers.class).info("test log line");
    }

    private static class TestResetLogHandlers
    {
    }
}
