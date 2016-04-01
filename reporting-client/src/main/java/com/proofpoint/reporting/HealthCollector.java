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
import com.google.inject.Inject;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.reporting.HealthResult.Status;

import javax.annotation.PostConstruct;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.reporting.HealthResult.healthResult;
import static java.util.Objects.requireNonNull;

class HealthCollector
{
    private final HealthBeanRegistry healthBeanRegistry;
    private final ScheduledExecutorService collectionExecutorService;
    private final HealthReporter healthReporter;
    private final String servicePrefix;

    @Inject
    HealthCollector(NodeInfo nodeInfo,
            HealthBeanRegistry healthBeanRegistry,
            @ForHealthCollector ScheduledExecutorService collectionExecutorService,
            HealthReporter healthReporter)
    {
        this.healthBeanRegistry = requireNonNull(healthBeanRegistry, "healthBeanRegistry is null");
        this.collectionExecutorService = requireNonNull(collectionExecutorService, "collectionExecutorService is null");
        this.healthReporter = requireNonNull(healthReporter, "healthReporter is null");
        servicePrefix = nodeInfo.getApplication() + " ";
    }

    @PostConstruct
    public void start()
    {
        collectionExecutorService.scheduleAtFixedRate(this::collectData, 10, 60, TimeUnit.SECONDS);
    }

    private void collectData()
    {
        ImmutableList.Builder<HealthResult> builder = ImmutableList.builder();
        for (Entry<String, HealthBeanAttribute> healthBeanAttributeEntry : healthBeanRegistry.getHealthAttributes().entrySet()) {
            Status status = Status.CRITICAL;
            String message;

            try {
                message = healthBeanAttributeEntry.getValue().getValue();
            }
            catch (AttributeNotFoundException e) {
                status = Status.UNKNOWN;
                message = "Health check attribute not found";
            }
            catch (MBeanException | ReflectionException e) {
                status = Status.UNKNOWN;
                message = e.getCause().getMessage();
            }

            if (message == null) {
                status = Status.OK;
            }

            String service = servicePrefix + healthBeanAttributeEntry.getKey();
            builder.add(healthResult(service, status, message));
        }

        healthReporter.reportResults(builder.build());
    }
}
