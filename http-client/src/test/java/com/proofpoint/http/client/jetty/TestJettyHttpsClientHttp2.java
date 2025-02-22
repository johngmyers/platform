package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.HttpClientConfig;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;

public class TestJettyHttpsClientHttp2
        extends TestJettyHttpsClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }
}
