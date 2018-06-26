/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin.core.util

enum class OptionalDependency(val displayName: String, val testClass: String, val groupId: String, val artifactId: String, val version: String) {
    JACKSON("Jackson", "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.core", "jackson-databind", "2.9.5"),
    VELOCITY("Velocity", "org.apache.velocity.app.VelocityEngine", "org.apache.velocity", "velocity", "1.7"),
    FREEMARKER("Freemarker", "freemarker.template.Configuration", "org.freemarker", "freemarker", "2.3.28"),
    THYMELEAF("Thymeleaf", "org.thymeleaf.TemplateEngine", "org.thymeleaf", "thymeleaf", "3.0.9.RELEASE"),
    MUSTACHE("Mustache", "com.github.mustachejava.MustacheFactory", "com.github.spullara.mustache.java", "compiler", "0.9.5"),
    JTWIG("Jtwig", "org.jtwig.environment.EnvironmentConfiguration", "org.jtwig", "jtwig-core", "5.87.0.RELEASE"),
    PEBBLE("Pebble", "com.mitchellbosecke.pebble.PebbleEngine", "com.mitchellbosecke", "pebble", "2.4.0"),
    COMMONMARK("Commonmark", "org.commonmark.renderer.html.HtmlRenderer", "com.atlassian.commonmark", "commonmark", "0.11.0"),
}

