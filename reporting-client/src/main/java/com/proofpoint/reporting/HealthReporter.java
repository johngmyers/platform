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

import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Charsets.UTF_8;
import static java.util.Objects.requireNonNull;

class HealthReporter
{
    private static final File DEV_NULL = new File("/dev/null");

    private final Logger log = Logger.get(getClass());
    private final HealthClientConfig healthClientConfig;
    private final ExecutorService invocationExecutorService;
    private final String hostname;

    @Inject
    HealthReporter(NodeInfo nodeInfo,
            HealthClientConfig healthClientConfig,
            @ForHealthReporter ExecutorService invocationExecutorService)
    {
        this.healthClientConfig = requireNonNull(healthClientConfig, "healthClientConfig is null");
        this.invocationExecutorService = requireNonNull(invocationExecutorService, "invocationExecutorService is null");
        hostname = nodeInfo.getInternalHostname();
    }

    void reportResults(Iterable<HealthResult> results)
    {
        if (!healthClientConfig.isEnabled()) {
            return;
        }

        try {
            for (HealthResult result : results) {
                try {
                    Process process = new ProcessBuilder("/usr/local/bin/nagios_manual_passive",
                            hostname,
                            result.getService(),
                            result.getStatus().name(),
                            result.getMessage())
                            .redirectInput(DEV_NULL)
                            .start();
                    invocationExecutorService.submit(() -> {
                        try (InputStream inputStream = process.getInputStream()) {
                            String output = CharStreams.toString(new InputStreamReader(inputStream, UTF_8));
                            if (!"".equals(output)) {
                                log.warn("Sending health status produced output: %s", output);
                            }
                        }
                        catch (IOException e) {
                            log.warn(e, "Reading standard output from sending health status threw exception");
                        }

                    });
                    invocationExecutorService.submit(() -> {
                        try (InputStream inputStream = process.getErrorStream()) {
                            String output = CharStreams.toString(new InputStreamReader(inputStream, UTF_8));
                            if (!"".equals(output)) {
                                log.warn("Sending health status produced error output: %s", output);
                            }
                        }
                        catch (IOException e) {
                            log.warn(e, "Reading standard error from sending health status threw exception");
                        }

                    });
                    process.waitFor();
                }
                catch (IOException e) {
                    log.warn(e, "Sending health status threw exception");
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
