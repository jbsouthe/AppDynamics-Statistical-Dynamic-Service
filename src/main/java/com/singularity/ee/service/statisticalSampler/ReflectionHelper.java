package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.util.system.SystemUtilsTranslateable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ReflectionHelper {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.ReflectionHelper");

    public static void setMaxEvents( Object eventServiceObject, int newMaxEvents ) {
        try {
            modifyPrivateFinalInt(eventServiceObject, "maxEventSize", newMaxEvents);
        } catch (Exception e) {
            logger.warn(String.format("Can not set the maxEventSize to %d, Exception: %s",newMaxEvents,e.getMessage()),e);
        }
    }

    public static int getMaxEvents( Object eventServiceObject ) {
        try {
            return getPrivateFinalInt(eventServiceObject, "maxEventSize");
        } catch (Exception e) {
            logger.warn(String.format("Can not get the maxEventSize from object sending from system property, Exception: %s",e.getMessage()),e);
            return SystemUtilsTranslateable.getIntProperty("appdynamics.agent.maxEvents", 100);
        }
    }

    public static void modifyPrivateFinalInt(Object object, String fieldName, int newValue) throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);

        // Check the Java version at runtime
        if (isJava9OrAbove()) {
            // Update the module to allow access to the field
            updateModuleAccessJava9Plus(object, "com.singularity.ee.agent.commonservices.eventgeneration");

            // Remove the final modifier using Java 9+ specific logic
            removeFinalModifierJava9Plus(field);
        } else {
            // Remove the final modifier using Java 8 specific logic
            removeFinalModifierJava8(field);
        }

        // Update the field value
        field.setInt(object, newValue);
    }

    private static void removeFinalModifierJava8(Field field) throws NoSuchFieldException, IllegalAccessException {
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }

    private static void removeFinalModifierJava9Plus(Field field) throws NoSuchFieldException, IllegalAccessException {
        field.getClass()
                .getDeclaredField("modifiers")
                .setAccessible(true);
        field.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }

    private static void updateModuleAccessJava9Plus(Object myObject, String addOpensWhat ) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        Class<?> moduleClass = Class.forName("java.lang.Module");
        Method getModuleMethod = Class.class.getMethod("getModule");
        Object declaringModule = getModuleMethod.invoke(myObject.getClass());
        Object currentModule = getModuleMethod.invoke(ReflectionHelper.class);

        Method addOpensMethod = currentModule.getClass().getMethod("addOpens", String.class, moduleClass);
        addOpensMethod.invoke(currentModule, addOpensWhat, declaringModule);

        /*
        Class<?> reflectionHelperClass = ReflectionHelper.class;
        Object declaringModule = declaringClass.getClassLoader()
                .loadClass("java.lang.Module")
                .getMethod("getModule")
                .invoke(declaringClass);
        Object reflectionExampleModule = reflectionHelperClass.getClassLoader()
                .loadClass("java.lang.Module")
                .getMethod("getModule")
                .invoke(reflectionHelperClass);

        Method addOpensMethod = reflectionExampleModule.getClass().getMethod("addOpens", String.class, reflectionExampleModule.getClass());
        addOpensMethod.invoke(reflectionExampleModule, addOpensWhat, declaringModule);

         */
    }

    private static boolean isJava9OrAbove() {
        String javaVersion = System.getProperty("java.version");
        int majorVersion = 8;
        if( javaVersion.contains(".") ) {
            majorVersion = Integer.parseInt(javaVersion.split("\\.")[1]);
        } else {
            majorVersion = Integer.parseInt(javaVersion);
        }
        return majorVersion > 8;
    }

    public static int getPrivateFinalInt(Object object, String fieldName) throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException {
        Field field = object.getClass().getDeclaredField(fieldName);
        if ( isJava9OrAbove() )
            updateModuleAccessJava9Plus(object, "com.singularity.ee.agent.commonservices.eventgeneration");
        field.setAccessible(true);
        // Retrieve the field value
        return field.getInt(object);
    }

    public static void addReportMetric( Object metricAggregator, long value ) {

        Method reportMethod = null;
        try {
            reportMethod = metricAggregator.getClass().getDeclaredMethod("_report", long.class);
            if (isJava9OrAbove()) {
                updateModuleAccessJava9Plus( metricAggregator, "com.singularity.ee.agent.commonservices.metricgeneration.aggregation");
            }
            reportMethod.setAccessible(true);
            reportMethod.invoke(metricAggregator, value);
        } catch (Exception e) {
            logger.warn(String.format("Can not call %s._report(%d), Exception: %s",metricAggregator.getClass().getName(), value, e.getMessage()), e);
        }

    }
}
