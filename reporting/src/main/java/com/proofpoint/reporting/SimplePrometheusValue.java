/*
 * Copyright 2018 Proofpoint, Inc.
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

import jakarta.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map.Entry;

import static com.proofpoint.reporting.ReportUtils.isReportable;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

record SimplePrometheusValue(Object value)
        implements PrometheusValue
{
    SimplePrometheusValue
    {
        requireNonNull(value, "value is null");
    }

    @Nullable
    static PrometheusValue simplePrometheusValue(@Nullable Object value) {
        if (value != null && isReportable(value) && value instanceof Number) {
            return new SimplePrometheusValue(value);
        }
        return null;
    }

    @Override
    public void writeMetric(BufferedWriter writer, String name, Iterable<Entry<String, String>> tags, @Nullable Long timestamp)
            throws IOException
    {
        writer.write(name);
        ReportUtils.writeTags(writer, tags);
        writer.append(' ');
        writer.write(value().toString());
        if (timestamp != null) {
            writer.append(' ');
            writer.write(Long.toString(NANOSECONDS.toMillis(timestamp)));
        }
        writer.append('\n');
    }
}
