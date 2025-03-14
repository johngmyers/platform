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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.platform.sample.PersonStore.StoreEntry;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import static java.util.Objects.requireNonNull;

@Path("/v1/person")
public class PersonsResource
{
    private final PersonStore store;

    @Inject
    public PersonsResource(PersonStore store)
    {
        this.store = requireNonNull(store, "store must not be null");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Person> listAll()
    {
        ImmutableMap.Builder<String, Person> builder = ImmutableMap.builder();
        for (StoreEntry entry : store.getAll()) {
            builder.put(entry.id(), entry.person());
        }
        return builder.build();
    }
}
