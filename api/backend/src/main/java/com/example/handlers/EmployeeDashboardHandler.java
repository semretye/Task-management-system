package com.example.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.sql.SQLClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class EmployeeDashboardHandler {

    private SQLClient dbClient;

    public EmployeeDashboardHandler(SQLClient dbClient) {
        this.dbClient = dbClient;
    }

    public void getEmployeeDashboardData(RoutingContext context) {
        String username = context.user().get("sub");

        // First, fetch the user information
        String userQuery = "SELECT id, username, role FROM users WHERE username = ?";

        dbClient.queryWithParams(userQuery, new JsonArray().add(username), userRes -> {
            if (userRes.failed()) {
                sendError(context, "Database error: " + userRes.cause().getMessage());
                return;
            }

            if (userRes.result().getRows().isEmpty()) {
                sendError(context, "User not found");
                return;
            }

            JsonObject user = userRes.result().getRows().get(0);
            final Integer userId = user.getInteger("id");

            // Now, fetch employee information
            String employeeQuery = "SELECT e.id, e.name, e.position, e.status " +
                                 "FROM employees e " +
                                 "WHERE e.user_id = ?";

            dbClient.queryWithParams(employeeQuery, new JsonArray().add(userId), employeeRes -> {
                if (employeeRes.failed()) {
                    sendError(context, "Failed to fetch employee data");
                    return;
                }

                final JsonObject response = new JsonObject()
                    .put("username", user.getString("username"))
                    .put("role", user.getString("role"));

                if (employeeRes.result().getRows().isEmpty()) {
                    // User has no employee record
                    response
                        .put("employeeId", 0)
                        .put("employeeName", user.getString("username"))
                        .put("position", "Not assigned")
                        .put("status", "INACTIVE")
                        .put("teams", new JsonArray().add(new JsonObject().put("name", "Unassigned")))
                        .put("tasks", new JsonArray())
                        .put("notifications", new JsonArray());

                    sendSuccessResponse(context, response);
                    return;
                }

                JsonObject employee = employeeRes.result().getRows().get(0);
                final Integer employeeId = employee.getInteger("id");

                // Fetch ALL teams information for the employee
                String teamQuery = "SELECT t.id, t.name FROM team_members tm " +
                                 "JOIN teams t ON tm.team_id = t.id " +
                                 "WHERE tm.employee_id = ?";

                dbClient.queryWithParams(teamQuery, new JsonArray().add(employeeId), teamRes -> {
                    JsonArray teamsArray = new JsonArray();
                    
                    if (teamRes.succeeded()) {
                        for (JsonObject team : teamRes.result().getRows()) {
                            teamsArray.add(new JsonObject()
                                .put("id", team.getInteger("id"))
                                .put("name", team.getString("name")));
                        }
                    }
                    
                    // If no teams, add default "Unassigned"
                    if (teamsArray.size() == 0) {
                        teamsArray.add(new JsonObject()
                            .put("id", 0)
                            .put("name", "Unassigned"));
                    }

                    // Get assigned tasks for the employee (both completed and incomplete)
                    String tasksQuery = "SELECT t.id, t.title as name, t.description, " +
                                      "t.due_date as dueDate, t.status, " +
                                      "t.priority " +
                                      "FROM tasks t " +
                                      "JOIN task_assignments ta ON t.id = ta.task_id " +
                                      "WHERE ta.employee_id = ? " +
                                      "ORDER BY t.due_date ASC";

                    dbClient.queryWithParams(tasksQuery, new JsonArray().add(employeeId), tasksRes -> {
                        final JsonArray tasks = new JsonArray();

                        if (tasksRes.succeeded()) {
                            if (!tasksRes.result().getRows().isEmpty()) {
                                // Format each task properly
                                for (JsonObject task : tasksRes.result().getRows()) {
                                    JsonObject formattedTask = new JsonObject()
                                        .put("id", task.getInteger("id"))
                                        .put("name", task.getString("name"))
                                        .put("description", task.getString("description"))
                                        .put("dueDate", task.getString("dueDate"))
                                        .put("status", task.getString("status"))
                                        .put("priority", task.getString("priority"))
                                        .put("progress", calculateTaskProgress(
                                            task.getString("dueDate"),
                                            task.getString("status")
                                        ));
                                    tasks.add(formattedTask);
                                }
                            }
                        } else {
                            System.err.println("Task query failed: " + tasksRes.cause().getMessage());
                        }

                        // Build the final response
                        response
                            .put("employeeId", employeeId)
                            .put("employeeName", employee.getString("name"))
                            .put("position", employee.getString("position"))
                            .put("status", employee.getString("status"))
                            .put("teams", teamsArray) // Now returns array of all teams
                            .put("tasks", tasks)
                            .put("notifications", getNotifications(employeeId));

                        sendSuccessResponse(context, response);
                    });
                });
            });
        });
    }

    // New endpoint for updating task status
    public void updateTaskStatus(RoutingContext context) {
        JsonObject body = context.getBodyAsJson();
        Integer taskId = body.getInteger("taskId");
        String newStatus = body.getString("status");
        String username = context.user().get("sub");

        // First verify the user is assigned to this task
        String verifyQuery = "SELECT ta.id FROM task_assignments ta " +
                           "JOIN employees e ON ta.employee_id = e.id " +
                           "JOIN users u ON e.user_id = u.id " +
                           "WHERE u.username = ? AND ta.task_id = ?";
        
        dbClient.queryWithParams(verifyQuery, new JsonArray().add(username).add(taskId), verifyRes -> {
            if (verifyRes.failed()) {
                sendError(context, "Database error: " + verifyRes.cause().getMessage());
                return;
            }
            
            if (verifyRes.result().getRows().isEmpty()) {
                sendError(context, "Task not found or not assigned to user");
                return;
            }
            
            // Update the task status
            String updateQuery = "UPDATE tasks SET status = ? WHERE id = ?";
            dbClient.updateWithParams(updateQuery, new JsonArray().add(newStatus).add(taskId), updateRes -> {
                if (updateRes.failed()) {
                    sendError(context, "Failed to update task status");
                    return;
                }
                
                // Return the updated task information
                String getTaskQuery = "SELECT id, title as name, due_date as dueDate, status, priority " +
                                    "FROM tasks WHERE id = ?";
                
                dbClient.queryWithParams(getTaskQuery, new JsonArray().add(taskId), taskRes -> {
                    if (taskRes.failed() || taskRes.result().getRows().isEmpty()) {
                        sendError(context, "Failed to fetch updated task");
                        return;
                    }
                    
                    JsonObject task = taskRes.result().getRows().get(0);
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("task", new JsonObject()
                            .put("id", task.getInteger("id"))
                            .put("name", task.getString("name"))
                            .put("dueDate", task.getString("dueDate"))
                            .put("status", task.getString("status"))
                            .put("progress", calculateTaskProgress(
                                task.getString("dueDate"),
                                task.getString("status")
                            )));
                    
                    sendSuccessResponse(context, response);
                });
            });
        });
    }

    private JsonArray getNotifications(Integer employeeId) {
        // Implement your notification logic here
        return new JsonArray()
            .add(new JsonObject()
                .put("id", 1)
                .put("message", "Welcome to the employee dashboard")
                .put("date", "2023-01-01")
                .put("read", false));
    }

    private void sendError(RoutingContext context, String message) {
        context.response()
            .setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("error", message).encodePrettily());
    }

    private void sendSuccessResponse(RoutingContext context, JsonObject response) {
        context.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encodePrettily());
    }

    // Improved progress calculation that considers task status
    private int calculateTaskProgress(String dueDateStr, String status) {
        if ("COMPLETED".equalsIgnoreCase(status)) {
            return 100;
        }
        
        try {
            LocalDate dueDate = LocalDate.parse(dueDateStr);
            LocalDate today = LocalDate.now();
            
            if (today.isAfter(dueDate)) {
                return 100; // Task is overdue
            }
            
            if (today.isEqual(dueDate)) {
                return 90; // Due today
            }
            
            LocalDate startDate = today.minusDays(7); // Assume task started 7 days before due date
            if (today.isBefore(startDate)) {
                return 0; // Task not started yet
            }
            
            long totalDays = ChronoUnit.DAYS.between(startDate, dueDate);
            long daysPassed = ChronoUnit.DAYS.between(startDate, today);
            
            return (int) ((daysPassed * 100) / totalDays);
        } catch (Exception e) {
            return 0;
        }
    }
}