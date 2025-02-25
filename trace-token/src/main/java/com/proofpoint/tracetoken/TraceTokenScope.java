/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.tracetoken;

import jakarta.annotation.Nullable;

import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;

/**
 * An object which, when closed, causes the thread's trace token state to be restored.
 */
public class TraceTokenScope
        implements AutoCloseable
{
    private final TraceToken oldToken;

    TraceTokenScope(@Nullable TraceToken oldToken)
    {
        this.oldToken = oldToken;
    }

    @Override
    public void close()
    {
        registerTraceToken(oldToken);
    }
}
