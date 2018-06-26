/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin.json

@FunctionalInterface
interface FromJsonMapper {
    fun <T> map(json: String, targetClass: Class<T>): T
}

@FunctionalInterface
interface ToJsonMapper {
    fun map(obj: Any): String
}

object JavalinJson {

    @JvmStatic
    var fromJsonMapper = object : FromJsonMapper {
        override fun <T> map(json: String, targetClass: Class<T>): T = JavalinJackson.fromJson(json, targetClass)
    }

    @JvmStatic
    var toJsonMapper = object : ToJsonMapper {
        override fun map(obj: Any): String = JavalinJackson.toJson(obj)
    }

}
