package com.example;

import com.example.handlers.TeamHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.jdbc.JDBCClient;
import java.util.Set;

public class TeamRoute extends AbstractVerticle {
    private final TeamHandler teamHandler;
    private final JWTAuth jwtAuth;

    public TeamRoute(JDBCClient dbClient, JWTAuth jwtAuth) {
        this.teamHandler = new TeamHandler(dbClient);
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
        
        // Team Routes
        router.get("/api/teams").handler(teamHandler::getAllTeams);
        router.post("/api/teams").handler(teamHandler::createTeams);
        router.put("/api/teams/:id").handler(teamHandler::updateTeams);
        router.delete("/api/teams/:id").handler(teamHandler::deleteTeams);

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8081)
            .onSuccess(server -> startPromise.complete())
            .onFailure(startPromise::fail);
    }
}
