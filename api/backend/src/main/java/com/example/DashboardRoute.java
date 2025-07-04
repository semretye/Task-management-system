package com.example;

import com.example.handlers.DashboardHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.jdbc.JDBCClient;
import java.util.HashSet;
import java.util.Set;

public class DashboardRoute extends AbstractVerticle {
    private final DashboardHandler dashboardHandler;
    private final JWTAuth jwtAuth;

    public DashboardRoute(JDBCClient dbClient, JWTAuth jwtAuth) {
        this.dashboardHandler = new DashboardHandler(vertx, dbClient);
        this.jwtAuth = jwtAuth;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // CORS Configuration
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("Authorization");
        allowedHeaders.add("Accept");

        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(HttpMethod.GET);
        allowedMethods.add(HttpMethod.POST);
        allowedMethods.add(HttpMethod.PUT);
        allowedMethods.add(HttpMethod.DELETE);

        router.route().handler(CorsHandler.create("*")
            .allowedHeaders(allowedHeaders)
            .allowedMethods(allowedMethods)
            .exposedHeaders(allowedHeaders));

        router.route().handler(BodyHandler.create());

        // JWT Middleware
        router.route("/api/*").handler(JWTAuthHandler.create(jwtAuth));

        // Dashboard route
        router.get("/api/dashboard/dashboard-data").handler(dashboardHandler::getDashboardData);
        router.get("/api/dashboard/manager-data").handler(dashboardHandler::getManagerDashboardData);
      

        // Start HTTP Server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8081)
            .onSuccess(server -> {
                System.out.println("Dashboard service started on port 8081");
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }
}