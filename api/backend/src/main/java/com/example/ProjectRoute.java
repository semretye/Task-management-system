package com.example;

import com.example.handlers.ProjectHandler;
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

public class ProjectRoute extends AbstractVerticle {
    private final ProjectHandler projectHandler;
    private final JWTAuth jwtAuth;

    public ProjectRoute(JDBCClient dbClient, JWTAuth jwtAuth) {
        this.projectHandler = new ProjectHandler(dbClient);
        this.jwtAuth = jwtAuth;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // CORS Configuration
        router.route().handler(CorsHandler.create("*")
            .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS))
            .allowedHeaders(Set.of("Content-Type", "Authorization", "Accept"))
            .exposedHeaders(Set.of("Authorization", "Content-Type"))
            .allowCredentials(true));

        // Body Handler (for parsing JSON body)
        router.route().handler(BodyHandler.create().setBodyLimit(1024 * 1024)); // 1MB limit

        // JWT Middleware for protected routes
        router.route("/api/projects*").handler(JWTAuthHandler.create(jwtAuth));
        
        // Project Routes
        router.get("/api/projects").handler(projectHandler::getAllProjects);
        router.post("/api/projects").handler(projectHandler::createProject);
        router.put("/api/projects/:id").handler(projectHandler::updateProject);
        router.delete("/api/projects/:id").handler(projectHandler::deleteProject);
        router.post("/api/projects/:projectId/assign-team").handler(projectHandler::assignTeamToProject);
        router.get("/api/projects/:projectId/teams").handler(projectHandler::getTeamsForProject);
        router.delete("/api/projects/:projectId/teams/:teamId").handler(projectHandler::removeTeamFromProject);
        router.get("/api/projects/:id").handler(projectHandler::getProjectById);
        // Project-Team Assignment Route
     

        // Start HTTP Server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8081)
            .onSuccess(server -> {
                System.out.println("Project service running on port 8081");
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }
}