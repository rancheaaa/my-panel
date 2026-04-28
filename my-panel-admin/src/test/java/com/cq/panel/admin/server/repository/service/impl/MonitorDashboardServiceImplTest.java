package com.cq.panel.admin.server.repository.service.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

class MonitorDashboardServiceImplTest {

    private final MonitorDashboardServiceImpl service = new MonitorDashboardServiceImpl(null, null, null, null, null);

    @Test
    void parseGranularityMillis_shouldSupportMinuteHourDay() throws Exception {
        Method method = MonitorDashboardServiceImpl.class.getDeclaredMethod("parseGranularityMillis", String.class);
        method.setAccessible(true);

        long oneMinute = (long) method.invoke(service, "1m");
        long fiveMinute = (long) method.invoke(service, "5m");
        long twoHour = (long) method.invoke(service, "2h");
        long threeDay = (long) method.invoke(service, "3d");

        Assertions.assertEquals(60_000L, oneMinute);
        Assertions.assertEquals(300_000L, fiveMinute);
        Assertions.assertEquals(7_200_000L, twoHour);
        Assertions.assertEquals(259_200_000L, threeDay);
    }

    @Test
    void compare_shouldMatchOperators() throws Exception {
        Method method = MonitorDashboardServiceImpl.class.getDeclaredMethod("compare", Double.class, String.class, Double.class);
        method.setAccessible(true);

        Assertions.assertTrue((boolean) method.invoke(service, 90D, "GT", 85D));
        Assertions.assertTrue((boolean) method.invoke(service, 85D, "GTE", 85D));
        Assertions.assertTrue((boolean) method.invoke(service, 80D, "LT", 85D));
        Assertions.assertTrue((boolean) method.invoke(service, 80D, "LTE", 80D));
        Assertions.assertTrue((boolean) method.invoke(service, 80D, "EQ", 80D));
        Assertions.assertTrue((boolean) method.invoke(service, 80D, "NE", 79D));
        Assertions.assertFalse((boolean) method.invoke(service, 80D, "GT", 85D));
    }
}
