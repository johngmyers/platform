/*
 * Copyright 2013 Proofpoint, Inc.
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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.reporting.HealthBinder.healthBinder;
import static com.proofpoint.json.JsonCodecBinder.jsonCodecBinder;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ReportingClientModule
    implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(ReportCollector.class).in(Scopes.SINGLETON);
        binder.bind(ReportClient.class).in(Scopes.SINGLETON);
        binder.bind(HealthCollector.class).in(Scopes.SINGLETON);

        binder.bind(CurrentTimeSecsProvider.class).to(SystemCurrentTimeSecsProvider.class).in(Scopes.SINGLETON);
        jsonCodecBinder(binder).bindJsonCodec(HealthReport.class);

        discoveryBinder(binder).bindDiscoveredHttpClient("reporting", ForReportClient.class);
        discoveryBinder(binder).bindSelector("monitoring-acceptor");
        httpClientBinder(binder).bindHttpClient("monitoring-acceptor", ForHealthCollector.class);
        bindConfig(binder).to(ReportClientConfig.class);

        binder.bind(ShutdownMonitor.class).in(Scopes.SINGLETON);
        healthBinder(binder).export(ShutdownMonitor.class);

        jaxrsBinder(binder).bindAdmin(HealthResource.class);
    }

    @Provides
    @ForReportCollector
    private static ScheduledExecutorService createCollectionExecutorService()
    {
        return newSingleThreadScheduledExecutor(daemonThreadsNamed("reporting-collector-%s"));
    }

    @Provides
    @ForReportClient
    private static ExecutorService createClientExecutorService()
    {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.NANOSECONDS, new LinkedBlockingQueue<>(5),
                        daemonThreadsNamed("reporting-client-%s"),
                        new DiscardOldestPolicy());
    }

    @Provides
    @ForHealthCollector
    private static ScheduledExecutorService createHealthCollectorExecutorService()
    {
        return newSingleThreadScheduledExecutor(daemonThreadsNamed("health-collector-%s"));
    }
}
