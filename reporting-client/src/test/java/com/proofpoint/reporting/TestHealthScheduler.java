/*
 * Copyright 2016 Proofpoint, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.testing.SerialScheduledExecutorService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.testing.Assertions.assertBetweenInclusive;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

public class TestHealthScheduler
{
    @Mock
    private HealthRegistrationLogger healthRegistrationLogger;
    private SerialScheduledExecutorService collectorExecutor;
    private HealthScheduler healthScheduler;

    @BeforeMethod
    public void setup()
    {
        initMocks(this);
        collectorExecutor = new SerialScheduledExecutorService();
        healthScheduler = new HealthScheduler(healthRegistrationLogger, collectorExecutor);
    }

    @Test
    public void testReportingModule()
    {
        Injector injector = Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of())),
                new JsonModule(),
                new ReportingModule(),
                new ReportingClientModule());
        injector.getInstance(ReportScheduler.class);
    }

    @Test
    public void testScheduling()
            throws Exception
    {
        healthScheduler.start();

        collectorExecutor.elapseTimeNanosecondBefore(1, MINUTES);
        verifyNoMoreInteractions(healthRegistrationLogger);

        collectorExecutor.elapseTime(1, NANOSECONDS);
        verify(healthRegistrationLogger).logHealthRegistrations();
        verifyNoMoreInteractions(healthRegistrationLogger);

        collectorExecutor.elapseTime(1000, DAYS);
        verifyNoMoreInteractions(healthRegistrationLogger);
    }
}
