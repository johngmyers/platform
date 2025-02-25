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
package com.proofpoint.configuration;

import com.google.inject.Key;
import jakarta.annotation.Nullable;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

record ConfigurationIdentity<T>(
        Class<T> configClass,

        @Nullable
        String prefix,

        // Must not participate in equals()
        @Nullable
        Key<T> key
)
{
    ConfigurationIdentity
    {
        requireNonNull(configClass, "configClass is null");
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigurationIdentity<?> that = (ConfigurationIdentity<?>) o;
        return Objects.equals(prefix, that.prefix) && Objects.equals(configClass, that.configClass);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(configClass, prefix);
    }
}
