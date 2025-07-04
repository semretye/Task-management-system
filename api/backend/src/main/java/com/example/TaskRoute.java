package com.example;

import com.example.handlers.TaskHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.impl.pool.Task;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.jdbc.JDBCClient;
import java.util.Set;

public class TaskRoute extends AbstractVerticle {
    private final TaskHandler taskHandler;
    private final JWTAuth jwtAuth;

    public TaskRoute(JDBCClient dbClient, JWTAuth jwtAuth) {
        this.taskHandler = new TaskHandler(dbClient);
        this.jwtAuth = jwtAuth;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);
        
        // CORS Configuration
        router.route().handler(CorsHandler.create("*")
            .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE))
            .allowedHeaders(Set.of("Content-Type", "Authorization"))
            .exposedHeaders(Set.of("Authorization")));
        
        router.route().handler(BodyHandler.create());
        
        // JWT Middleware
        router.route().handler(JWTAuthHandler.create(jwtAuth));
        
        // Employee Routes
        router.get("/api/tasks").handler(taskHandler::getAllTasks);
      
        router.post("/api/task-assignments").handler(taskHandler::assignTask);
        router.post("/api/tasks").handler(taskHandler::createTasks);
        router.put("/api/tasks/:id").handler(taskHandler::updateTasks);
        router.delete("/api/tasks/:id").handler(taskHandler::deleteTasks);
        router.get("/api/task-assignments").handler(taskHandler::getAllTaskAssignments);
        router.put("/api/tasks/:id/status").handler(taskHandler::updateTaskStatus);

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8081)
            .onSuccess(server -> startPromise.complete())
            .onFailure(startPromise::fail);
    }
}