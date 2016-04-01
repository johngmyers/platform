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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@AutoValue
abstract class HealthResult
{
    @JsonProperty("service")
    abstract String getService();

    @JsonProperty("status")
    abstract Status getStatus();

    @Nullable
    @JsonProperty("message")
    abstract String getMessage();

    static HealthResult healthResult(String service,
            Status status,
            @Nullable String message)
    {
        return new AutoValue_HealthResult(service, status, message);
    }

    enum Status
    {
        OK, WARNING, CRITICAL, UNKNOWN;
    }
}