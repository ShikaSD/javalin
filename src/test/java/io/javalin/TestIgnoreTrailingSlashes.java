/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 *
 */

package io.javalin;

import io.javalin.util.SimpleHttpClient;
import java.io.IOException;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Created by enchanting on 03.08.17.
 */
public class TestIgnoreTrailingSlashes {

    private SimpleHttpClient simpleHttpClient = new SimpleHttpClient();

    @Test
    public void dontIgnoreTrailingSlashes() throws IOException {
        Javalin app = Javalin.create()
            .port(0)
            .dontIgnoreTrailingSlashes()
            .start()
            .get("/hello", ctx -> ctx.result("Hello, slash!"));
        assertThat(simpleHttpClient.http_GET("http://localhost:" + app.port() + "/hello").getBody(), is("Hello, slash!"));
        assertThat(simpleHttpClient.http_GET("http://localhost:" + app.port() + "/hello/").getBody(), is("Not found"));
        app.stop();
    }

    @Test
    public void ignoreTrailingSlashes() throws IOException {
        Javalin app = Javalin.create()
            .port(0)
            .start()
            .get("/hello", ctx -> ctx.result("Hello, slash!"));
        assertThat(simpleHttpClient.http_GET("http://localhost:" + app.port() + "/hello").getBody(), is("Hello, slash!"));
        assertThat(simpleHttpClient.http_GET("http://localhost:" + app.port() + "/hello/").getBody(), is("Hello, slash!"));
        app.stop();
    }

}
