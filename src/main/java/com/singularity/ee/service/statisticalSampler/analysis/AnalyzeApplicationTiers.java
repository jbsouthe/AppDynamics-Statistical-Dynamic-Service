package com.singularity.ee.service.statisticalSampler.analysis;

import com.singularity.ee.service.statisticalSampler.MetaData;
import com.singularity.ee.service.statisticalSampler.analysis.util.Utility;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class AnalyzeApplicationTiers {
    private static final Logger logger = LogManager.getFormatterLogger("AnalyzeApplicationTiers");
    private Controller controller;
    private Map<String,Double> confidenceMap, errorMap;

    public AnalyzeApplicationTiers(Properties configProperties, String application, String tier, String confidenceLevel, String marginOfError) throws MalformedURLException {
        logger.debug("Starting with properties: %s", configProperties);
        logger.info("Running for Application: '%s' Tier: '%s' Confidence Level: %s Margin of Error: %s",
                (application==null? "All" : application), (tier==null? "All" : tier), confidenceLevel, marginOfError);

        this.confidenceMap = new HashMap<>();
        this.confidenceMap.put("80%", 1.28);
        this.confidenceMap.put("85%", 1.44);
        this.confidenceMap.put("90%", 1.64);
        this.confidenceMap.put("95%", 1.96);
        this.confidenceMap.put("98%", 2.33);
        this.confidenceMap.put("99%", 2.58);

        this.errorMap = new HashMap<>();
        this.errorMap.put("2%", 0.02);
        this.errorMap.put("5%", 0.05);
        this.errorMap.put("7%", 0.07);
        this.errorMap.put("10%", 0.1);

        this.controller = new Controller(configProperties.getProperty("controller-url"), configProperties.getProperty("api-key"), configProperties.getProperty("api-secret"), application, tier);

    }

    public static void main( String ... args ) {

        ArgumentParser parser = ArgumentParsers.newFor("AnalyzeApplicationTiers")
                .singleMetavar(true)
                .build()
                .defaultHelp(true)
                .version(String.format("AnalyzeApplicationTiers Agent %s Tool %s build date %s\n\tby %s\n\tvisit %s for the most up to date information.",
                        MetaData.SERVICENAME, MetaData.VERSION, MetaData.BUILDTIMESTAMP, MetaData.GECOS, MetaData.DEVNET))
                .description("Analyze Controller Application Tiers and recommend percentage of nodes to collect data from using Cochran's formula to determine ideal sample size");
        parser.addArgument("-v", "--version").action(Arguments.version());
        parser.addArgument("-c", "--config")
                .setDefault("config.properties")
                .metavar("Configuration Properties")
                .help("Use this specific config properties file.");
        parser.addArgument("-a", "--application")
                .metavar("Application")
                .help("Only analyze a specific application");
        parser.addArgument("-t", "--tier")
                .metavar("Tier")
                .help("Only analyze a specific tier");
        parser.addArgument("--confidence")
                .metavar("Confidence Level")
                .choices("80%", "85%", "90%", "95%", "98%", "99%")
                .setDefault("90%")
                .help("Target Confidence Level: {\"80%\", \"85%\", \"90%\", \"95%\", \"98%\", \"99%\"}");
        parser.addArgument("--error")
                .metavar("Margin of Error")
                .choices("2%", "5%", "7%", "10%")
                .setDefault("5%")
                .help("Target Margin of Error: {\"2%\", \"5%\", \"7%\", \"10%\"}");
        parser.addArgument("-d", "--debug")
                .metavar("Verbose logging level")
                .choices("WARN", "INFO", "DEBUG", "TRACE")
                .setDefault("INFO")
                .help("Print debug level logging during run: {\"WARN\", \"INFO\", \"DEBUG\", \"TRACE\"}");

        Namespace namespace = null;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();

        builder.setStatusLevel(Level.INFO);
        // naming the logger configuration
        builder.setConfigurationName("DefaultLogger");

        // create a console appender
        AppenderComponentBuilder appenderBuilder = builder.newAppender("Console", "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
        // add a layout like pattern, json etc
        appenderBuilder.add(builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d %p %c [%t] %m%n"));
        RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Utility.getLevel(namespace.getString("debug")));
        rootLogger.add(builder.newAppenderRef("Console"));

        builder.add(appenderBuilder);
        builder.add(rootLogger);
        Configurator.reconfigure(builder.build());

        Properties configProperties = new Properties();
        try {
            configProperties.load( new FileInputStream( namespace.getString("config")));
        } catch (Exception e) {
            parser.printHelp();
            logger.error("Error reading config properties file "+ namespace.getString("config") +" Exception: "+ e, e);
            System.exit(1);
        }

        try {
            AnalyzeApplicationTiers analyzeApplicationTiers = new AnalyzeApplicationTiers( configProperties,
                    namespace.getString("application"), namespace.getString("tier"),
                    namespace.getString("confidence"), namespace.getString("error"));
        } catch (MalformedURLException e) {
            logger.error("Error in controller-url config property '%s' Exception: %s", configProperties.getProperty("controller-url"), e.getMessage());
            System.exit(1);
        }
    }
}
