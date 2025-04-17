package com.example;

import com.example.handlers.EmployeeHandler;
import com.example.handlers.ProjectHandler;
import com.example.handlers.TeamHandler;
import com.example.handlers.TaskHandler;
import com.example.handlers.DashboardHandler;
import com.example.handlers.EmployeeDashboardHandler;

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

public class EmployeeRoute extends AbstractVerticle {
    private final EmployeeHandler employeeHandler;
    private final ProjectHandler projectHandler;
    private final TeamHandler teamHandler;
    private final TaskHandler taskHandler;
    private final DashboardHandler dashboardHandler;
    private final EmployeeDashboardHandler employeeDashboardHandler;
    

    private final JDBCClient dbClient;
    private final JWTAuth jwtAuth;

    public EmployeeRoute(JDBCClient dbClient, JWTAuth jwtAuth) {
        this.dbClient = dbClient;
        this.jwtAuth = jwtAuth;

        // Initialize handlers
        this.employeeHandler = new EmployeeHandler(dbClient);
        this.projectHandler = new ProjectHandler(dbClient);
        this.teamHandler = new TeamHandler(dbClient);
        this.taskHandler = new TaskHandler(dbClient);

        
        // Initialize the DashboardHandler properly
        this.dashboardHandler = new DashboardHandler(vertx,dbClient);
        this.employeeDashboardHandler= new EmployeeDashboardHandler(dbClient);
        // Correctly initializing DashboardHandler
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // CORS Configuration
        router.route().handler(CorsHandler.create("*")
            .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH))
            .allowedHeaders(Set.of("Content-Type", "Authorization"))
            .exposedHeaders(Set.of("Authorization")));

        router.route().handler(BodyHandler.create());

        // Public route (no auth required)
        router.get("/api/projects").handler(projectHandler::getAllProjects);

        // JWT Middleware - Apply JWT authentication for routes starting with /api
        router.route("/api/*").handler(JWTAuthHandler.create(jwtAuth));

        // Employee Routes
        router.get("/api/employees").handler(employeeHandler::getAllEmployees);
        router.get("/api/users").handler(employeeHandler::getallusers);
        router.post("/api/employees").handler(employeeHandler::createEmployee);
        router.patch("/api/employees/:id").handler(employeeHandler::updateEmployee);
        router.delete("/api/employees/:id").handler(employeeHandler::deleteEmployee);

        // Project Routes
        router.post("/api/projects").handler(projectHandler::createProject);
        router.put("/api/projects/:id").handler(projectHandler::updateProject);
        router.delete("/api/projects/:id").handler(projectHandler::deleteProject);
        router.get("/api/projects/:id").handler(projectHandler::getProjectById);

        // Team Routes
        router.get("/api/teams").handler(teamHandler::getAllTeams);
        router.post("/api/teams").handler(teamHandler::createTeams);
        router.put("/api/teams/:id").handler(teamHandler::updateTeams);
        router.delete("/api/teams/:id").handler(teamHandler::deleteTeams);

        // Task Routes
        router.get("/api/tasks").handler(taskHandler::getAllTasks);
        router.post("/api/tasks").handler(taskHandler::createTasks);
        router.put("/api/tasks/:id").handler(taskHandler::updateTasks);
        router.delete("/api/tasks/:id").handler(taskHandler::deleteTasks);

        // Task Assignment Routes
        router.post("/api/task-assignments").handler(taskHandler::assignTask);
        router.get("/api/task-assignments").handler(taskHandler::getAllTaskAssignments);
        router.put("/api/task-assignments/:id").handler(taskHandler::updateTaskAssignment);
        router.delete("/api/task-assignments/:id").handler(taskHandler::deleteTaskAssignment);

        // Project-Team Routes
        router.post("/api/projects/:projectId/assign-team").handler(projectHandler::assignTeamToProject);
        router.get("/api/projects/:projectId/teams").handler(projectHandler::getTeamsForProject);

        // Dashboard Route - accessing dashboard data
        router.get("/api/dashboard/dashboard-data").handler(dashboardHandler::getDashboardData);
        router.get("/api/dashboard/manager-data").handler(dashboardHandler::getManagerDashboardData);
     router.get("/api/employee/dashboard-data").handler(employeeDashboardHandler::getEmployeeDashboardData);
     router.put("/api/tasks/:id/status").handler(taskHandler::updateTaskStatus);
     router.delete("/api/projects/:projectId/teams/:teamId").handler(projectHandler::removeTeamFromProject);
        // Start the HTTP server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8081)
            .onSuccess(server -> {
                System.out.println("Server started on port 8081");
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }
}
