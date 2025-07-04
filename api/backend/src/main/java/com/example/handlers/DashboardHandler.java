package com.example.handlers;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.web.RoutingContext;

import java.time.LocalDateTime;
import java.util.List;

public class DashboardHandler {
    private final Vertx vertx;
    private final SQLClient dbClient;

    public DashboardHandler(Vertx vertx, SQLClient dbClient) {
        this.vertx = vertx;
        this.dbClient = dbClient;
    }

    public void getDashboardData(RoutingContext ctx) {
        JsonObject dashboardData = new JsonObject();
    
        String countsQuery = "SELECT " +
            "(SELECT COUNT(*) FROM employees) AS totalEmployees, " +
            "(SELECT COUNT(*) FROM projects) AS totalProjects, " +
            "(SELECT COUNT(*) FROM teams) AS totalTeams, " +
            "(SELECT COUNT(*) FROM tasks) AS totalTasks";
    
        dbClient.querySingle(countsQuery, countsRes -> {
            if (countsRes.failed()) {
                sendError(ctx, "Failed to fetch dashboard counts: " + countsRes.cause().getMessage());
                return;
            }
    
            JsonArray row = countsRes.result(); 
            JsonObject counts = new JsonObject()
                .put("totalEmployees", row.getInteger(0))
                .put("totalProjects", row.getInteger(1))
                .put("totalTeams", row.getInteger(2))
                .put("totalTasks", row.getInteger(3));
    
            dashboardData.mergeIn(counts);
    
            String tasksQuery = "SELECT id, title, description, " +
                "DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s') AS created_at " +
                "FROM tasks ORDER BY created_at DESC LIMIT 5";
    
            dbClient.query(tasksQuery, tasksRes -> {
                if (tasksRes.failed()) {
                    sendError(ctx, "Failed to fetch recent tasks: " + tasksRes.cause().getMessage());
                    return;
                }
    
                JsonArray recentTasks = new JsonArray(tasksRes.result().getRows()); 
                dashboardData.put("recentTasks", recentTasks);
    
                sendSuccessResponse(ctx, dashboardData);
            });
        });
    }
    public void getManagerDashboardData(RoutingContext ctx) {
        JsonObject dashboardData = new JsonObject();
        
        // Updated query with clearer column names and consistent status checks
        String managerQuery = "SELECT " +
            "(SELECT COUNT(*) FROM projects) AS totalProjects, " +
            "(SELECT COUNT(*) FROM tasks) AS totalTasks, " +
            "(SELECT COUNT(*) FROM tasks WHERE status = 'ASSIGNED') AS assignedTasks, " + 
            "(SELECT COUNT(*) FROM tasks WHERE status = 'IN_PROGRESS' OR status = 'IN PROGRESS') AS inProgressTasks, " + // More flexible
            "(SELECT COUNT(*) FROM tasks WHERE status = 'COMPLETED') AS completedTasks, " +
            "(SELECT COUNT(*) FROM teams) AS totalTeams, " +
            "(SELECT COUNT(*) FROM employees) AS totalEmployees, " +
            "(SELECT COUNT(*) FROM employees WHERE status = 'ACTIVE') AS activeEmployees, " +
            "(SELECT COUNT(*) FROM employees WHERE status = 'ON_LEAVE') AS onLeaveEmployees, " +
            "(SELECT ROUND(AVG(progress), 2) FROM projects) AS projectProgress, " + 
            "(SELECT ROUND(AVG(DATEDIFF(end_date, CURDATE())), 0) FROM projects WHERE end_date >= CURDATE()) AS daysRemaining"; // Only future dates
    
        dbClient.query(managerQuery, queryRes -> {
            if (queryRes.failed()) {
                sendError(ctx, "Failed to fetch manager dashboard data: " + queryRes.cause().getMessage());
                return;
            }
    
            List<JsonObject> rows = queryRes.result().getRows();
            if (rows.isEmpty()) {
                sendError(ctx, "No dashboard data found");
                return;
            }
    
            JsonObject result = rows.get(0);
    
            // Updated response with all needed fields
            JsonObject response = new JsonObject()
                .put("totalProjects", result.getInteger("totalProjects", 0))
                .put("totalTasks", result.getInteger("totalTasks", 0))
                .put("assignedTasks", result.getInteger("assignedTasks", 0)) // New
                .put("inProgressTasks", result.getInteger("inProgressTasks", 0))
                .put("completedTasks", result.getInteger("completedTasks", 0))
                .put("totalTeams", result.getInteger("totalTeams", 0))
                .put("totalEmployees", result.getInteger("totalEmployees", 0))
                .put("activeEmployees", result.getInteger("activeEmployees", 0))
                .put("onLeaveEmployees", result.getInteger("onLeaveEmployees", 0))
                .put("projectProgress", result.getDouble("projectProgress", 0.0))
                .put("daysRemaining", result.getInteger("daysRemaining", 0)); // Changed to Integer
    
            sendSuccessResponse(ctx, response);
        });
    }

   
    private void sendSuccessResponse(RoutingContext ctx, JsonObject data) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(data.encodePrettily());
    }

    private void sendError(RoutingContext ctx, String message) {
        ctx.response()
            .setStatusCode(500)  // Default error status
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("error", true)
                .put("message", message)
                .encodePrettily());
    }
}
