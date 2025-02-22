package com.proofpoint.discovery.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import jakarta.annotation.Nullable;

import java.net.URI;

public class AdaptingAnnouncementHttpServerInfoProvider implements Provider<AnnouncementHttpServerInfo>
{
    private com.proofpoint.http.server.announce.AnnouncementHttpServerInfo delegate;

    public AdaptingAnnouncementHttpServerInfoProvider()
    {
    }

    @Inject(optional = true)
    public void setDelegate(com.proofpoint.http.server.announce.AnnouncementHttpServerInfo delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public AnnouncementHttpServerInfo get()
    {
        if (delegate == null) {
            throw new ProvisionException("No binding for com.proofpoint.http.server.announce.AnnouncementHttpServerInfo");
        }

        return new AnnouncementHttpServerInfo()
        {
            @Nullable
            @Override
            public URI getHttpUri()
            {
                return delegate.getHttpUri();
            }

            @Nullable
            @Override
            public URI getHttpExternalUri()
            {
                return delegate.getHttpExternalUri();
            }

            @Nullable
            @Override
            public URI getHttpsUri()
            {
                return delegate.getHttpsUri();
            }

            @Nullable
            @Override
            public URI getAdminUri()
            {
                return delegate.getAdminUri();
            }

        };
    }
}
