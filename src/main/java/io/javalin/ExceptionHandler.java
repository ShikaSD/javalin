/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin;

/**
 * A handler for use with {@link Javalin#exception(Class, ExceptionHandler)} and process general exceptions
 * which {@link Handler} can produce.
 * @see Context
 * @see <a href="https://javalin.io/documentation#exception-mapping">Exception mapping in docs</a>
 */
@FunctionalInterface
public interface ExceptionHandler<T extends Exception> {
    void handle(T exception, Context ctx);
}
