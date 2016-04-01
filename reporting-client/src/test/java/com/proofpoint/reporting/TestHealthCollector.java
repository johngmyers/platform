/*
 * Copyright 2017 Proofpoint, Inc.
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
import com.google.common.collect.Iterables;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.testing.SerialScheduledExecutorService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.util.List;

import static com.google.common.base.Throwables.propagate;
import static com.proofpoint.reporting.HealthResult.Status.CRITICAL;
import static com.proofpoint.reporting.HealthResult.Status.OK;
import static com.proofpoint.reporting.HealthResult.Status.UNKNOWN;
import static com.proofpoint.reporting.HealthResult.healthResult;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

public class TestHealthCollector
{
    private SerialScheduledExecutorService executorService;
    private HealthBeanRegistry registry;
    private HealthCollector collector;
    @Mock
    private HealthReporter healthReporter;
    @Captor
    private ArgumentCaptor<List<HealthResult>> resultCaptor;

    @BeforeMethod
    public void setup()
    {
        initMocks(this);
        executorService = new SerialScheduledExecutorService();
        registry = new HealthBeanRegistry();
        collector = new HealthCollector(
                new NodeInfo("testenvironment"),
                registry,
                executorService,
                healthReporter);
        collector.start();
    }

    @Test
    public void testNoHealthBeans()
    {
        executorService.elapseTime(1, MINUTES);
        verify(healthReporter).reportResults(ImmutableList.of());
    }

    @Test
    public void testOkStatus()
    {
        registerTestAttribute("test description", null);

        assertSendsHealthReport(healthResult("test-application test description (suffix)", OK, null));
    }

    @Test
    public void testCriticalStatus()
    {
        registerTestAttribute("test description", "test status");

        assertSendsHealthReport(healthResult("test-application test description (suffix)", CRITICAL, "test status"));
    }

    @Test
    public void testAttributeNotFoundException()
    {
        registerTestAttributeException("test description", new AttributeNotFoundException("test exception"));

        assertSendsHealthReport(healthResult("test-application test description (suffix)", UNKNOWN, "Health check attribute not found"));
    }

    @Test
    public void testMBeanException()
    {
        registerTestAttributeException("test description", new MBeanException(new Exception("test exception"), "mbean exception"));

        assertSendsHealthReport(healthResult("test-application test description (suffix)", UNKNOWN, "test exception"));
    }

    @Test
    public void testReflectionException()
    {
        registerTestAttributeException("test description", new ReflectionException(new Exception("test exception"), "reflection exception"));

        assertSendsHealthReport(healthResult("test-application test description (suffix)", UNKNOWN, "test exception"));
    }

    private void assertSendsHealthReport(HealthResult expected)
    {
        executorService.elapseTime(1, MINUTES);

        verify(healthReporter).reportResults(resultCaptor.capture());

        assertEquals(Iterables.getOnlyElement(resultCaptor.getValue()), expected);
    }

    private void registerTestAttribute(final String description, @Nullable final String value)
    {
        try {
            registry.register(new HealthBeanAttribute()
            {
                @Override
                public String getDescription()
                {
                    return description;
                }

                @Override
                public boolean isRemoveFromRotation()
                {
                    return false;
                }

                @Override
                public String getValue()
                {
                    return value;
                }
            }, description + " (suffix)");
        }
        catch (InstanceAlreadyExistsException e) {
            throw propagate(e);
        }
    }

    private void registerTestAttributeException(final String description, final Exception e)
    {
        try {
            registry.register(new HealthBeanAttribute()
            {
                @Override
                public String getDescription()
                {
                    return description;
                }

                @Override
                public boolean isRemoveFromRotation()
                {
                    return false;
                }

                @Override
                public String getValue()
                        throws AttributeNotFoundException, MBeanException, ReflectionException
                {
                    if (e instanceof AttributeNotFoundException) {
                        throw (AttributeNotFoundException) e;
                    }
                    if (e instanceof MBeanException) {
                        throw (MBeanException) e;
                    }
                    if (e instanceof ReflectionException) {
                        throw (ReflectionException) e;
                    }
                    throw propagate(e);
                }
            }, description + " (suffix)");
        }
        catch (InstanceAlreadyExistsException ex) {
            throw propagate(ex);
        }
    }
}
