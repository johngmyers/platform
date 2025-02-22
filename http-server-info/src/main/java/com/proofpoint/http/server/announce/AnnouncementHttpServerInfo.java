package com.proofpoint.http.server.announce;

import java.net.URI;

import jakarta.annotation.Nullable;

public interface AnnouncementHttpServerInfo
{
    @Nullable
    URI getHttpUri();
    @Nullable
    URI getHttpExternalUri();

    @Nullable
    URI getHttpsUri();

    @Nullable
    default URI getAdminUri() {
        return null;
    }
}
