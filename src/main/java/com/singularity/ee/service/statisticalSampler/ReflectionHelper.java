package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.util.system.SystemUtilsTranslateable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ReflectionHelper {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.ReflectionHelper");

    public static void setMaxEvents( Object eventServiceObject, int newMaxEvents ) {
        try {
            Field field = eventServiceObject.getClass().getDeclaredField("maxEventSize");
            field.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers"); //these are private after java 1.8
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            field.set(eventServiceObject, newMaxEvents);
        } catch (Exception e) {
            logger.error(String.format("Can not set the maxEventSize to %d, Exception: %s",newMaxEvents,e.getMessage()),e);
        }
    }

    public static int getMaxEvents( Object eventServiceObject ) {
        try {
            Field field = eventServiceObject.getClass().getDeclaredField("maxEventSize");
            field.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers"); //these are private after java 1.8
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            return (int) field.get(eventServiceObject);
        } catch (Exception e) {
            logger.error(String.format("Can not get the maxEventSize from object sending from system property, Exception: %s",e.getMessage()),e);
            return SystemUtilsTranslateable.getIntProperty("appdynamics.agent.maxEvents", 100);
        }
    }
}
