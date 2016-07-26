package com.proofpoint.platform.sample;

import com.proofpoint.discovery.client.ServiceType;
import com.proofpoint.http.client.HttpClient;

import javax.inject.Inject;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class DosClient
{
    private final HttpClient client;
    private final ScheduledExecutorService executor;

    @Inject
    public DosClient(@ServiceType("reporting") HttpClient client)
    {
        this.client = client;
        executor = newSingleThreadScheduledExecutor(daemonThreadsNamed("dos-%s"));
        executor.scheduleAtFixedRate(this::sendRequest, 1, 500, TimeUnit.MILLISECONDS);
    }

    private void sendRequest()
    {
        client.executeAsync(prepareGet().setUri(URI.create("/")).build(), createStringResponseHandler());
    }
}
