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
package com.proofpoint.configuration;

import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;
import org.testng.annotations.Test;

import static com.proofpoint.testing.Assertions.assertContains;
import static com.proofpoint.testing.Assertions.assertContainsAllOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class ProblemsTest
{
    @Test
    public void TestOneError()
    {
        Problems problems = new Problems();
        problems.addError("message %d", 1);

        Message[] errors = problems.getErrors().toArray(new Message[0]);
        assertEquals(errors.length, 1);
        assertContainsAllOf(errors[0].toString(), "Error", "message 1");

        assertEquals(problems.getWarnings().size(), 0, "Found unexpected warnings in problem object");

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertContainsAllOf(e.getMessage(), "message 1");
        }
    }

    @Test
    public void TestTwoErrors()
    {
        Problems problems = new Problems();
        problems.addError("message %d", 1);
        problems.addError("message %d", 2);

        Message[] errors = problems.getErrors().toArray(new Message[0]);
        assertEquals(errors.length, 2);
        assertContainsAllOf(errors[0].toString(), "Error", "message 1");
        assertContainsAllOf(errors[1].toString(), "Error", "message 2");

        assertEquals(problems.getWarnings().size(), 0, "Found unexpected warnings in problem object");


        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertContainsAllOf(e.getMessage(), "message 1", "message 2");
        }
    }

    @Test
    public void TestFormatError()
    {
        Problems problems = new Problems();
        problems.addError("message %d", "NaN");

        Message[] errors = problems.getErrors().toArray(new Message[0]);
        assertEquals(errors.length, 1);
        assertContainsAllOf(errors[0].toString(), "Error", "message %d", "NaN", "IllegalFormatConversionException");

        assertEquals(problems.getWarnings().size(), 0, "Found unexpected warnings in problem object");

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertContains(e.getMessage(), "message %d [NaN]");
        }
    }

    @Test
    public void TestOneWarning()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", 1);

        assertEquals(problems.getErrors().size(), 0, "Found unexpected errors in problem object");

        Message[] warnings = problems.getWarnings().toArray(new Message[0]);
        assertEquals(warnings.length, 1);
        assertContainsAllOf(warnings[0].toString(), "Warning", "message 1");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            fail("Didn't expect problems object to throw", cause);
        }
    }

    @Test
    public void TestTwoWarnings()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", 1);
        problems.addWarning("message %d", 2);

        assertEquals(problems.getErrors().size(), 0, "Found unexpected errors in problem object");

        Message[] warnings = problems.getWarnings().toArray(new Message[0]);
        assertEquals(warnings.length, 2);
        assertContainsAllOf(warnings[0].toString(), "Warning", "message 1");
        assertContainsAllOf(warnings[1].toString(), "Warning", "message 2");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            fail("Didn't expect problems object to throw", cause);
        }
    }

    @Test
    public void TestFormatWarning()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", "NaN");

        assertEquals(problems.getErrors().size(), 0, "Found unexpected errors in problem object");

        Message[] warnings = problems.getWarnings().toArray(new Message[0]);
        assertEquals(warnings.length, 1);
        assertContainsAllOf(warnings[0].toString(), "Warning", "message %d", "NaN", "IllegalFormatConversionException");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            fail("Didn't expect problems object to throw", cause);
        }
    }

    @Test
    public void TestErrorsAndWarnings()
    {
        Problems problems = new Problems();
        problems.addError("message e%d", 1);
        problems.addError("message e%d", 2);

        problems.addWarning("message w%d", 1);
        problems.addWarning("message w%d", 2);
        problems.addWarning("message w%d", 3);

        Message[] errors = problems.getErrors().toArray(new Message[0]);
        assertEquals(errors.length, 2);
        assertContainsAllOf(errors[0].toString(), "Error", "message e1");
        assertContainsAllOf(errors[1].toString(), "Error", "message e2");

        Message[] warnings = problems.getWarnings().toArray(new Message[0]);
        assertEquals(warnings.length, 3);
        assertContainsAllOf(warnings[0].toString(), "Warning", "message w1");
        assertContainsAllOf(warnings[1].toString(), "Warning", "message w2");
        assertContainsAllOf(warnings[2].toString(), "Warning", "message w3");

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertContainsAllOf(e.getMessage(), "message e1", "message e2", "message w1", "message w2", "message w3");
        }
    }

    private static class SimpleMonitor implements Problems.Monitor
    {
        private String result = "";

        @Override
        public void onError(Message error)
        {
            result = result + "E-" + error.getMessage() + ", ";
        }

        @Override
        public void onWarning(Message warning)
        {
            result = result + "W-" + warning.getMessage() + ", ";
        }

        public String getResult()
        {
            return result;
        }
    }

    @Test
    public void TestMonitor()
    {
        SimpleMonitor monitor = new SimpleMonitor();
        Problems problems = new Problems(monitor);

        problems.addError("1");
        problems.addWarning("1");
        problems.addWarning("2");
        problems.addWarning("3");
        problems.addError("2");

        assertContains(monitor.getResult(), "E-Error: 1, W-Warning: 1, W-Warning: 2, W-Warning: 3, E-Error: 2");
    }
}
