package com.singularity.ee.service.statisticalSampler;


import java.io.File;
import java.lang.reflect.Field;

public class ReflectionHelperTest {

    @org.junit.Test
    public void testSetMaxEvents() {
        int start=100; int end=50;
        TestClass testClass = new TestClass(start);

        /*
        for(Field field : Field.class.getDeclaredFields() ) {
            System.out.println("Field "+ field.toString());
        }
         */
        int getMaxEventsValue = ReflectionHelper.getMaxEvents(testClass);
        assert getMaxEventsValue == start;
        ReflectionHelper.setMaxEvents(testClass, end);
        assert ReflectionHelper.getMaxEvents(testClass) == end;
        System.out.println(String.format("orig: %d get: %d set: %s", start, getMaxEventsValue, testClass));
    }

    private class TestClass {
        private final int maxEventSize;

        private TestClass(int maxEventSize) {
            this.maxEventSize = maxEventSize;
        }

        public String toString() { return String.format("maxEventSize=%d",this.maxEventSize); }
    }
}