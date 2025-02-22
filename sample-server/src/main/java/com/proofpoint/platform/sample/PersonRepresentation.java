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
package com.proofpoint.platform.sample;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PersonRepresentation(
        @Nullable
        @NotNull(message = "is missing")
        @Pattern(regexp = "[^@]+@[^@]+", message = "is malformed")
        String email,

        @Nullable
        @NotNull(message = "is missing")
        String name
)
{
    @JsonCreator
    public PersonRepresentation
    {
    }

    public Person toPerson()
    {
        return new Person(email, name);
    }
}
