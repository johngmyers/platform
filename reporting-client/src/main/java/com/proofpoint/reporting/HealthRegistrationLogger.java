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
import com.google.inject.Inject;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.log.LoggingConfiguration;
import com.proofpoint.node.NodeInfo;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.reporting.HealthCheckRegistrationRepresentation.healthCheckRegistrationRepresentation;
import static com.proofpoint.reporting.HealthRegistrationsRepresentation.healthRegistrationsRepresentation;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Objects.requireNonNull;

public class HealthRegistrationLogger
{
    private static final JsonCodec<HealthRegistrationsRepresentation> HEALTH_REGISTRATIONS_REPRESENTATION_JSON_CODEC = jsonCodec(HealthRegistrationsRepresentation.class);
    private static final Logger log = Logger.get(HealthRegistrationLogger.class);

    private final HealthBeanRegistry healthBeanRegistry;
    private final LoggingConfiguration loggingConfiguration;
    private final String descriptionPrefix;

    @Inject
    HealthRegistrationLogger(HealthBeanRegistry healthBeanRegistry, LoggingConfiguration loggingConfiguration, NodeInfo nodeInfo)
    {
        this.healthBeanRegistry = requireNonNull(healthBeanRegistry, "healthBeanRegistry is null");
        this.loggingConfiguration = requireNonNull(loggingConfiguration, "loggingConfiguration is null");
        descriptionPrefix = requireNonNull(nodeInfo, "nodeInfo is null").getApplication() + " ";
    }

    public void logHealthRegistrations()
    {
        if (loggingConfiguration.getLogPath() == null) {
            return;
        }
        Path parent = Paths.get(loggingConfiguration.getLogPath()).getParent();
        if (parent == null) {
            return;
        }

        Path logPath = parent.resolve("healthchecks.json");
        Path logPathNew = parent.resolve("newhealthchecks.json");

        ImmutableList.Builder<HealthCheckRegistrationRepresentation> builder = ImmutableList.builder();
        for (String description : healthBeanRegistry.getHealthAttributes().keySet()) {
            builder.add(healthCheckRegistrationRepresentation(descriptionPrefix + description));
        }

        HealthRegistrationsRepresentation registrations = healthRegistrationsRepresentation(builder.build());

        try {
            Files.write(logPathNew, HEALTH_REGISTRATIONS_REPRESENTATION_JSON_CODEC.toJsonBytes(registrations), TRUNCATE_EXISTING, CREATE
            );
            Files.move(logPathNew, logPath, ATOMIC_MOVE, REPLACE_EXISTING);
        }
        catch (IOException e) {
            log.error(e, "Failed to write health check registrations");
        }
    }
}
