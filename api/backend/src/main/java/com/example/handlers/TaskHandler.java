package com.example.handlers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;

public class TaskHandler {
    private final JDBCClient dbClient;

    public TaskHandler(JDBCClient dbClient) {
        this.dbClient = dbClient;
    }

    private void executeQuery(String query, JsonArray params, RoutingContext context, String successMessage) {
        dbClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                context.response().end(successMessage);
            } else {
                context.response().setStatusCode(500).end(res.cause().getMessage());
            }
        });
    }

    public void getAllTasks(RoutingContext context) {
        dbClient.query("SELECT * FROM tasks", res -> {
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
                    .end(jsonArray.encode());
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to fetch tasks")
                        .put("details", res.cause().getMessage())
                        .encode());
            }
        });
    }
    public void createTasks(RoutingContext context) {
        try {
            JsonObject task = context.body().asJsonObject();
    
            if (task.getString("title") == null || task.getString("dueDate") == null ||
                task.getString("projectId") == null || task.getString("progress") == null) {
                context.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Missing required fields: title, dueDate, projectId, or progress").encode());
                return;
            }
    
            String title = task.getString("title");
            int projectId = Integer.parseInt(task.getString("projectId"));
            float progress = Float.parseFloat(task.getString("progress"));
    
            // Check if a task with the same title already exists
            dbClient.queryWithParams("SELECT COUNT(*) as count FROM tasks WHERE title = ?", new JsonArray().add(title), checkRes -> {
                if (checkRes.succeeded()) {
                    int count = checkRes.result().iterator().next().getInteger("count");
                    if (count > 0) {
                        context.response()
                            .setStatusCode(409) // Conflict
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", "A task with this title already exists.").encode());
                    } else {
                        // Proceed to insert task
                        dbClient.updateWithParams(
                            "INSERT INTO tasks(title, description, due_date, priority, status, project_id, progress) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            new JsonArray()
                                .add(title)
                                .add(task.getString("description"))
                                .add(task.getString("dueDate"))
                                .add(task.getString("priority"))
                                .add(task.getString("status"))
                                .add(projectId)
                                .add(progress),
                            res -> {
                                if (res.succeeded()) {
                                    context.response()
                                        .setStatusCode(201)
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject()
                                            .put("message", "Task created successfully")
                                            .put("success", true)
                                            .encode());
                                } else {
                                    context.response()
                                        .setStatusCode(500)
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject().put("error", res.cause().getMessage()).encode());
                                }
                            }
                        );
                    }
                } else {
                    context.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", "Failed to check existing task").encode());
                }
            });
    
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Invalid projectId or progress").encode());
        } catch (Exception e) {
            e.printStackTrace();
            context.response().setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Server error: " + e.getMessage()).encode());
        }
    }
    
    
    public void updateTasks(RoutingContext context) {
        String idParam = context.pathParam("id");
    
        if (idParam == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Task ID is required").encode());
            return;
        }
    
        JsonObject taskJson = context.getBodyAsJson();
    
        if (taskJson == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing request body").encode());
            return;
        }
    
        try {
            int id = Integer.parseInt(idParam);
            String title = taskJson.getString("title");
            String description = taskJson.getString("description");
            String dueDate = taskJson.getString("dueDate");
            String priority = taskJson.getString("priority");
            String status = taskJson.getString("status");
            Integer projectId = taskJson.containsKey("projectId") ? taskJson.getInteger("projectId") : null;
            Float progress = taskJson.containsKey("progress") ? Float.parseFloat(taskJson.getValue("progress").toString()) : null;
    
            if (title == null || dueDate == null || projectId == null || progress == null) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Missing required fields: title, dueDate, projectId, or progress").encode());
                return;
            }
    
            String sql = """
                UPDATE tasks
                SET title = ?, description = ?, due_date = ?, priority = ?, status = ?, project_id = ?, progress = ?
                WHERE id = ?
            """;
    
            JsonArray params = new JsonArray()
                    .add(title)
                    .add(description)
                    .add(dueDate)
                    .add(priority)
                    .add(status)
                    .add(projectId)
                    .add(progress)
                    .add(id);
    
            dbClient.updateWithParams(sql, params, ar -> {
                if (ar.succeeded()) {
                    context.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("message", "Task updated successfully").encode());
                } else {
                    Throwable cause = ar.cause();
                    cause.printStackTrace();
                    context.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", "Error updating task: " + cause.getMessage()).encode());
                }
            });
    
        } catch (NumberFormatException e) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Invalid task ID").encode());
        } catch (Exception e) {
            e.printStackTrace();
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Internal server error: " + e.getMessage()).encode());
        }
    }
    
    
    public void deleteTasks(RoutingContext context) {
        String id = context.pathParam("id");

        dbClient.updateWithParams(
            "DELETE FROM tasks WHERE id = ?",
            new JsonArray().add(id),
            res -> {
                if (res.succeeded()) {
                    context.response().end();
                } else {
                    context.fail(res.cause());
                }
            }
        );
    }
    public void assignTask(RoutingContext context) {
        try {
            // Validate content type
            if (!context.request().getHeader("Content-Type").contains("application/json")) {
                context.response()
                    .setStatusCode(400)
                    .end("Content-Type must be application/json");
                return;
            }
    
            // Get and validate request body
            JsonObject body = context.getBodyAsJson();
            if (body == null) {
                context.response()
                    .setStatusCode(400)
                    .end("Request body must be valid JSON");
                return;
            }
    
            // Safely extract and validate parameters
            Integer taskId = safeGetInteger(body, "taskId");
            Integer employeeId = safeGetInteger(body, "employeeId");
            String startDate = body.getString("startDate");
            String endDate = body.getString("endDate");
    
            if (taskId == null || employeeId == null) {
                context.response()
                    .setStatusCode(400)
                    .end("Both taskId and employeeId must be provided as numbers");
                return;
            }
    
            if (startDate == null || endDate == null) {
                context.response()
                    .setStatusCode(400)
                    .end("Both startDate and endDate must be provided");
                return;
            }
    
            // Query to check if the task is already assigned to another employee
            String checkQuery = "SELECT * FROM task_assignments WHERE task_id = ?";
            dbClient.queryWithParams(checkQuery, new JsonArray().add(taskId), res -> {
                if (res.succeeded()) {
                    // Use getRows() to get the rows and check size
                    if (res.result().getRows().size() > 0) {
                        // Task already assigned, respond with an error
                        context.response()
                            .setStatusCode(400)
                            .end(new JsonObject()
                                .put("error", "This task has already been assigned.")
                                .encode());
                    } else {
                        // Proceed with inserting the assignment if task is not assigned
                        String insertQuery = "INSERT INTO task_assignments (task_id, employee_id, start_date, end_date) VALUES (?, ?, ?, ?)";
                        dbClient.updateWithParams(insertQuery, 
                            new JsonArray().add(taskId).add(employeeId).add(startDate).add(endDate), 
                            insertRes -> {
                                if (insertRes.succeeded()) {
                                    context.response()
                                        .setStatusCode(200)
                                        .end(new JsonObject()
                                            .put("success", true)
                                            .put("message", "Task assigned successfully")
                                            .encode());
                                } else {
                                    handleDatabaseError(context, insertRes.cause());
                                }
                            });
                    }
                } else {
                    // Handle database query error
                    handleDatabaseError(context, res.cause());
                }
            });
            
        } catch (Exception e) {
            context.response()
                .setStatusCode(500)
                .end(new JsonObject()
                    .put("success", false)
                    .put("error", "Internal server error")
                    .encode());
        }
    }
    
    
    
    // Helper method to safely get integer values from JSON
    private Integer safeGetInteger(JsonObject json, String key) {
        try {
            Object value = json.getValue(key);
            if (value == null) return null;
            
            if (value instanceof Number) {
                return ((Number)value).intValue();
            } else if (value instanceof String) {
                try {
                    return Integer.parseInt((String)value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    public void getAllTaskAssignments(RoutingContext context) {
        // Modified query to join with tasks and employees tables
        String query = "SELECT ta.id, ta.task_id as taskId, ta.employee_id as employeeId, " +
                       "ta.start_date as startDate, ta.end_date as endDate, " +
                       "t.title as taskTitle, e.name as employeeName " +
                       "FROM task_assignments ta " +
                       "JOIN tasks t ON ta.task_id = t.id " +
                       "JOIN employees e ON ta.employee_id = e.id";
        
        dbClient.query(query, res -> {
            if (res.succeeded()) {
                List<JsonObject> rows = res.result().getRows();
                JsonArray jsonArray = new JsonArray();
                
                // Process each row to handle date formatting if needed
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
                context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                        .put("error", "Failed to fetch task assignments")
                        .put("details", res.cause().getMessage())
                        .encode());
            }
        });
    }
    public void updateTaskStatus(RoutingContext ctx) {
        int taskId = Integer.parseInt(ctx.pathParam("id"));
        JsonObject body = ctx.getBodyAsJson();
        String status = body.getString("status");
        int progress = body.getInteger("progress", 0);
    
        String sql = "UPDATE tasks SET status = ?, progress = ? WHERE id = ?";
    
        dbClient.updateWithParams(sql, new JsonArray().add(status).add(progress).add(taskId), ar -> {
            if (ar.succeeded()) {
                ctx.response().setStatusCode(204).end();
            } else {
                ctx.fail(500, ar.cause());
            }
        });
    }
    public void updateTaskAssignment(RoutingContext context) {
        try {
            String id = context.pathParam("id");
            JsonObject assignment = context.getBodyAsJson();
            
            // Validate required fields
            if (assignment.getInteger("taskId") == null || 
                assignment.getInteger("employeeId") == null ||
                assignment.getString("startDate") == null) {
                context.response()
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("error", "Missing required fields: taskId, employeeId, or startDate")
                        .encode());
                return;
            }
            
            String query = "UPDATE task_assignments SET " +
                          "task_id = ?, employee_id = ?, start_date = ?, end_date = ? " +
                          "WHERE id = ?";
            
            JsonArray params = new JsonArray()
                .add(assignment.getInteger("taskId"))
                .add(assignment.getInteger("employeeId"))
                .add(assignment.getString("startDate"))
                .add(assignment.getString("endDate")) // can be null
                .add(Integer.parseInt(id));
                
            dbClient.updateWithParams(query, params, res -> {
                if (res.succeeded()) {
                    context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                            .put("message", "Task assignment updated successfully")
                            .encode());
                } else {
                    handleDatabaseError(context, res.cause());
                }
            });
        } catch (Exception e) {
            context.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid request format")
                    .put("details", e.getMessage())
                    .encode());
        }
    }
    
    public void deleteTaskAssignment(RoutingContext context) {
        try {
            String id = context.pathParam("id");
            
            String query = "DELETE FROM task_assignments WHERE id = ?";
            
            dbClient.updateWithParams(query, new JsonArray().add(Integer.parseInt(id)), res -> {
                if (res.succeeded()) {
                    context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                            .put("message", "Task assignment deleted successfully")
                            .encode());
                } else {
                    handleDatabaseError(context, res.cause());
                }
            });
        } catch (Exception e) {
            context.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid ID format")
                    .put("details", e.getMessage())
                    .encode());
        }
    }
    
    // Helper method to handle database errors
    private void handleDatabaseError(RoutingContext context, Throwable cause) {
        // Log the error for debugging
        cause.printStackTrace();
        
        // Check for common database errors
        if (cause.getMessage().contains("foreign key constraint")) {
            context.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("success", false)
                    .put("error", "Invalid task or employee ID")
                    .encode());
        } else {
            context.response()
                .setStatusCode(500)
                .end(new JsonObject()
                    .put("success", false)
                    .put("error", "Database operation failed")
                    .encode());
        }
    }

}
