package com.proofpoint.jmx;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.rmi.server.RMIServerSocketFactory;

public class LoopbackRMIServerSocketFactory
        implements RMIServerSocketFactory
{
    private final InetAddress loopbackAddress;

    public LoopbackRMIServerSocketFactory()
    {
        try {
            loopbackAddress = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        }
        catch (UnknownHostException e) {
            throw new AssertionError("Could not get local ip address");
        }
    }

    @Override
    public ServerSocket createServerSocket(int port)
            throws IOException
    {
        return ServerSocketFactory.getDefault()
                .createServerSocket(port, 0, loopbackAddress);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        return obj.getClass().equals(getClass());
    }

    @Override
    public int hashCode() {
        return LoopbackRMIServerSocketFactory.class.hashCode();
    }
}

