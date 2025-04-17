package com.example.handlers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;

public class ProjectHandler {

    private final JDBCClient dbClient;

    public ProjectHandler(JDBCClient dbClient) {
        this.dbClient = dbClient;
    }

    private void executeQuery(String query, JsonArray params, RoutingContext context, String successMessage) {
        dbClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("success", true).put("message", successMessage).encode());
            } else {
                context.response().setStatusCode(500).end(res.cause().getMessage());
            }
        });
    }

    public void getAllProjects(RoutingContext context) {
        dbClient.query("SELECT * FROM projects", res -> {
            if (res.succeeded()) {
                List<JsonObject> rows = res.result().getRows();
                JsonArray jsonArray = new JsonArray();
                for (JsonObject row : rows) {
                    JsonObject processed = new JsonObject();
                    row.forEach(entry -> {
                        Object value = entry.getValue();
                        if (value instanceof LocalDate || value instanceof LocalDateTime) {
                            processed.put(entry.getKey(), value.toString());
                        } else {
                            processed.put(entry.getKey(), value);
                        }
                    });
                    jsonArray.add(processed);
                }
    
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(jsonArray.encodePrettily());
            } else {
                context.response().setStatusCode(500).end(res.cause().getMessage());
            }
        });
    }
    

    public void createProject(RoutingContext context) {
        try {
            JsonObject project = context.body().asJsonObject();
            
            // Validate required fields with more flexible naming
            if (project == null || 
                !project.containsKey("name") || 
                !project.containsKey("description") ||
                (!project.containsKey("startDate") && !project.containsKey("start_date")) ||
                (!project.containsKey("endDate") && !project.containsKey("end_date"))) {
                
                context.response()
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("message", "Missing required fields")
                        .encode());
                return;
            }
    
            // Handle both camelCase and snake_case field names
            String name = project.getString("name");
            String description = project.getString("description");
            String startDateStr = project.getString("startDate", project.getString("start_date"));
            String endDateStr = project.getString("endDate", project.getString("end_date"));
            int progress = project.getInteger("progress", 0);
    
            // Date parsing and validation
            LocalDate startDate, endDate;
            try {
                startDate = LocalDate.parse(startDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                endDate = LocalDate.parse(endDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                
                if (endDate.isBefore(startDate)) {
                    context.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                            .put("success", false)
                            .put("message", "End date must be after start date")
                            .encode());
                    return;
                }
            } catch (DateTimeParseException e) {
                context.response()
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("message", "Invalid date format. Use YYYY-MM-DD")
                        .encode());
                return;
            }
    
            // Execute the insert query
            executeQuery(
                "INSERT INTO projects(name, description, start_date, end_date, progress) VALUES (?, ?, ?, ?, ?)",
                new JsonArray()
                    .add(name)
                    .add(description)
                    .add(startDate.toString())
                    .add(endDate.toString())
                    .add(progress),
                context,
                "Project created successfully"
            );
        } catch (Exception e) {
            context.response()
                .setStatusCode(500)
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", "Internal server error: " + e.getMessage())
                    .encode());
        }
    }
    public void updateProject(RoutingContext context) {
        String id = context.pathParam("id");
        JsonObject project = context.getBodyAsJson();
    
        try {
            LocalDate startDate = LocalDate.parse(project.getString("start_date").substring(0, 10));
            LocalDate endDate = LocalDate.parse(project.getString("end_date").substring(0, 10));
            int progress = project.getInteger("progress", 0);
    
            // Validate progress
            if (progress < 0 || progress > 100) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("success", false)
                        .put("message", "Progress must be between 0 and 100")
                        .encode());
                return;
            }
    
            dbClient.updateWithParams(
                "UPDATE projects SET name = ?, description = ?, start_date = ?, end_date = ?, progress = ? WHERE id = ?",
                new JsonArray()
                    .add(project.getString("name"))
                    .add(project.getString("description"))
                    
                    .add(startDate.toString())
                    .add(endDate.toString())
                    .add(progress)
                    .add(Integer.parseInt(id)),
                res -> {
                    if (res.succeeded()) {
                        context.response()
                            .putHeader("Content-Type", "application/json")
                            .setStatusCode(200)
                            .end(new JsonObject().put("message", "Project updated successfully").encode());
                    } else {
                        context.response().setStatusCode(500).end(res.cause().getMessage());
                    }
                }
            );
        } catch (Exception e) {
            context.response().setStatusCode(400).end("Invalid date format or invalid progress value");
        }
    }
    

    public void deleteProject(RoutingContext context) {
        String id = context.pathParam("id");

        dbClient.updateWithParams(
            "DELETE FROM projects WHERE id = ?",
            new JsonArray().add(id),
            res -> {
                if (res.succeeded()) {
                    int rowsDeleted = res.result().getUpdated();
                    if (rowsDeleted == 0) {
                        context.response().setStatusCode(404).end("Project not found");
                    } else {
                        context.response().setStatusCode(204).end();
                    }
                } else {
                    context.response().setStatusCode(500).end("Error deleting project: " + res.cause().getMessage());
                }
            });
    }

    public void assignTeamToProject(RoutingContext context) {
        try {
            String projectIdParam = context.pathParam("projectId");
            JsonObject body = context.getBodyAsJson();
    
            if (projectIdParam == null || body == null || !body.containsKey("teamId")) {
                context.response().setStatusCode(400).end("Missing projectId or teamId");
                return;
            }
    
            int projectId;
            try {
                projectId = Integer.parseInt(projectIdParam);
            } catch (NumberFormatException e) {
                context.response().setStatusCode(400).end("Invalid projectId format");
                return;
            }
    
            int teamId;
            try {
                Object teamIdValue = body.getValue("teamId");
                if (teamIdValue instanceof Number) {
                    teamId = ((Number) teamIdValue).intValue();
                } else if (teamIdValue instanceof String) {
                    teamId = Integer.parseInt((String) teamIdValue);
                } else {
                    context.response().setStatusCode(400).end("teamId must be a number");
                    return;
                }
            } catch (NumberFormatException e) {
                context.response().setStatusCode(400).end("Invalid teamId format");
                return;
            }
    
            String query = "INSERT INTO project_teams (project_id, team_id) VALUES (?, ?)";
            JsonArray params = new JsonArray().add(projectId).add(teamId);
    
            dbClient.updateWithParams(query, params, res -> {
                if (res.succeeded()) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("message", "Team assigned to project").encode());
                } else {
                    String errorMsg = res.cause().getMessage();
                    res.cause().printStackTrace(); // Optional: log for debugging
    
                    if (errorMsg.contains("Duplicate entry")) {
                        context.response()
                            .setStatusCode(409)
                            .end(new JsonObject().put("error", "Team is already assigned to this project").encode());
                    } else if (errorMsg.contains("foreign key")) {
                        context.response()
                            .setStatusCode(400)
                            .end(new JsonObject().put("error", "Invalid projectId or teamId (foreign key constraint)").encode());
                    } else {
                        context.response()
                            .setStatusCode(500)
                            .end(new JsonObject().put("error", "Database error: " + errorMsg).encode());
                    }
                }
            });
    
        } catch (Exception e) {
            e.printStackTrace(); // Optional: log
            context.response()
                .setStatusCode(500)
                .end(new JsonObject().put("error", "Internal server error").encode());
        }
    }
    

    public void removeTeamFromProject(RoutingContext context) {
        String projectIdParam = context.pathParam("projectId");
        String teamIdParam = context.pathParam("teamId");
    
        System.out.println("Incoming request to remove team: projectId = " + projectIdParam + ", teamId = " + teamIdParam);
    
        if (projectIdParam == null || teamIdParam == null) {
            context.response().setStatusCode(400).end("Missing projectId or teamId");
            return;
        }
    
        try {
            int projectId = Integer.parseInt(projectIdParam);
            int teamId = Integer.parseInt(teamIdParam);
    
            System.out.println("Parsed values: projectId = " + projectId + ", teamId = " + teamId);
    
            // Log the query being executed for further debugging
            String query = "DELETE FROM project_teams WHERE project_id = ? AND team_id = ?";
            JsonArray params = new JsonArray().add(projectId).add(teamId);
    
            // Log the query and parameters being passed to the database
            System.out.println("Executing query: " + query + " with parameters: " + params.encode());
    
            dbClient.updateWithParams(query, params, res -> {
                if (res.succeeded()) {
                    int rowsDeleted = res.result().getUpdated();
                    System.out.println("Rows deleted: " + rowsDeleted); // Log how many rows were affected
    
                    if (rowsDeleted == 0) {
                        // If no rows were deleted, the team was not found in the project
                        System.out.println("No team found for projectId: " + projectId + " and teamId: " + teamId);
                        context.response().setStatusCode(404).end("Team not associated with project");
                    } else {
                        // Success
                        System.out.println("Successfully removed team from project");
                        context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("message", "Team removed from project").encode());
                    }
                } else {
                    // Log the cause of failure
                    res.cause().printStackTrace();
                    context.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("error", "Failed to remove team: " + res.cause().getMessage()).encode());
                }
            });
    
        } catch (NumberFormatException e) {
            // Handle invalid number format in projectId or teamId
            context.response().setStatusCode(400).end("Invalid projectId or teamId format");
        } catch (Exception e) {
            // Catch any other unexpected errors
            e.printStackTrace();
            context.response()
                .setStatusCode(500)
                .end(new JsonObject().put("error", "Internal server error").encode());
        }
    }
    public void getTeamsForProject(RoutingContext ctx) {
        String projectIdParam = ctx.pathParam("projectId");

        if (projectIdParam == null) {
            ctx.response().setStatusCode(400).end("Project ID is required.");
            return;
        }

        int projectId = Integer.parseInt(projectIdParam);
        String query = "SELECT t.* FROM teams t JOIN project_teams pt ON pt.team_id = t.id WHERE pt.project_id = ?";

        dbClient.queryWithParams(query, new JsonArray().add(projectId), res -> {
            if (res.succeeded()) {
                JsonArray teamArray = new JsonArray(res.result().getRows());
                ctx.response()
                   .putHeader("Content-Type", "application/json")
                   .end(teamArray.encode());
            } else {
                ctx.response().setStatusCode(500).end("Failed to fetch teams.");
            }
        });
    }

    public void getProjectById(RoutingContext context) {
        String id = context.pathParam("id");
        dbClient.queryWithParams("SELECT * FROM projects WHERE id = ?", new JsonArray().add(id), res -> {
            if (res.succeeded()) {
                List<JsonObject> rows = res.result().getRows();
                if (!rows.isEmpty()) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(rows.get(0).encodePrettily());
                } else {
                    context.response().setStatusCode(404).end("Project not found");
                }
            } else {
                context.response().setStatusCode(500).end(res.cause().getMessage());
            }
        });
    }
}
