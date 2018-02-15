package com.proofpoint.stats;

import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import static com.proofpoint.testing.Assertions.assertGreaterThanOrEqual;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.testng.Assert.assertEquals;

public class TestJmxGcMonitor
{
    @Test
    public void test()
            throws Exception
    {
        JmxGcMonitor gcMonitor = new JmxGcMonitor();
        assertEquals(gcMonitor.getMajorGcCount(), 0);
        assertEquals(gcMonitor.getMajorGcTime(), new Duration(0, NANOSECONDS));
        try {
            gcMonitor.start();
            assertGreaterThanOrEqual(gcMonitor.getMajorGcCount(), (long) 0);
            assertGreaterThanOrEqual(gcMonitor.getMajorGcTime(), new Duration(0, NANOSECONDS));
        }
        finally {
            gcMonitor.stop();
        }
    }
}
