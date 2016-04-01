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
package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.HealthTester;
import com.proofpoint.reporting.ReportingModule;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestDiscoveryModule
{
    private Injector injector;

    @BeforeMethod
    public void setup()
    {
        injector = Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of("testing.discovery.uri", "fake://server"))),
                new JsonModule(),
                new TestingNodeModule(),
                new ReportingModule(),
                binder -> {
                    binder.bind(MBeanExporter.class).in(Scopes.SINGLETON);
                    discoveryBinder(binder).bindServiceAnnouncement(serviceAnnouncement("test").build());
                },
                new DiscoveryModule()
        );
    }

    @Test
    public void testBinding()
            throws Exception
    {
        // should produce a discovery announcement client and a lookup client
        assertNotNull(injector.getInstance(DiscoveryAnnouncementClient.class));
        assertNotNull(injector.getInstance(DiscoveryLookupClient.class));
        // should produce an Announcer
        assertNotNull(injector.getInstance(Announcer.class));
    }

    @Test
    public void testExecutorShutdown()
            throws Exception
    {
        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new JsonModule(),
                        new TestingNodeModule(),
                        new DiscoveryModule(),
                        new ReportingModule(),
                        new TestingMBeanModule()
                );

        Injector injector = app.initialize();

        ExecutorService executor = injector.getInstance(Key.get(ScheduledExecutorService.class, ForDiscoveryClient.class));
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        assertFalse(executor.isShutdown());
        lifeCycleManager.stop();
        assertTrue(executor.isShutdown());
    }

    @Test
    public void testHealthCheckRegistered()
    {
        injector.getInstance(Announcer.class).start();
        HealthTester.assertHealthCheckRegistered(injector, "Discovery announcement");
    }

    @Test
    public void testHealthCheckNotRegisteredNotStarted()
    {
        injector.getInstance(Announcer.class);
        HealthTester.assertHealthCheckNotRegistered(injector, "Discovery announcement");
    }
}
