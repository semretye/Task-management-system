package com.example.handlers;

import java.time.LocalDateTime;
import java.util.List;


import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;
public class EmployeeHandler {
    private final JDBCClient dbClient;

    public EmployeeHandler(JDBCClient dbClient) {
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
      public void getAllEmployees(RoutingContext context) {
        System.out.println("Attempting to fetch employees...");
        
        dbClient.query("SELECT * FROM employees", res -> {
            if (res.succeeded()) {
                System.out.println("Query succeeded, processing results...");
                List<JsonObject> rows = res.result().getRows();
                
                if (rows == null || rows.isEmpty()) {
                    System.out.println("No employees found in database");
                    context.response()
                        .setStatusCode(404)
                        .end(new JsonObject().put("message", "No employees found").encode());
                    return;
                }
    
                List<JsonObject> processedRows = rows.stream().map(row -> {
                    JsonObject json = new JsonObject();
                    row.forEach(entry -> {
                        if (entry.getValue() instanceof LocalDateTime) {
                            json.put(entry.getKey(), entry.getValue().toString());
                        } else {
                            json.put(entry.getKey(), entry.getValue());
                        }
                    });
                    return json;
                }).toList();
    
                JsonArray jsonArray = new JsonArray(processedRows);
                System.out.println("Returning employees: " + jsonArray.encodePrettily());
                
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(jsonArray.encodePrettily());
            } else {
                System.err.println("Database query failed: " + res.cause().getMessage());
                context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                        .put("error", "Database error")
                        .put("details", res.cause().getMessage())
                        .encode());
            }
        });
    }

public void getallusers(RoutingContext context) {
   
    dbClient.query("SELECT * FROM users", res -> {
        if (res.succeeded()) {
            List<JsonObject> rows = res.result().getRows();

            
            // Convert LocalDateTime fields to string
            List<JsonObject> processedRows = rows.stream().map(row -> {
                JsonObject json = new JsonObject();
                row.forEach(entry -> {
                    if (entry.getValue() instanceof LocalDateTime) {
                        // Convert LocalDateTime to String
                        json.put(entry.getKey(), entry.getValue().toString());
                    } else {
                        json.put(entry.getKey(), entry.getValue());
                    }
                });
                return json;
            }).toList();

            JsonArray jsonArray = new JsonArray(processedRows);  

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(jsonArray.encodePrettily());
        } else {
            context.response().setStatusCode(500).end(res.cause().getMessage());
        }
    });
}


   
public void createEmployee(RoutingContext context) {
    System.out.println("....................." + context.body().asJsonObject());
    try {
        JsonObject employee = context.body().asJsonObject();
        
        // Assuming you have a field for user_id in the request
        String userId = employee.getString("user_id");
        String status = employee.getString("status", "ACTIVE"); // Default to "ACTIVE" if not provided
        
        if (userId == null) {
            context.response().setStatusCode(400).end("user_id is required");
            return;
        }

        executeQuery(
            "INSERT INTO employees(name, position, salary, user_id, status) VALUES (?, ?, ?, ?, ?)",
            new JsonArray()
                .add(employee.getString("name"))
                .add(employee.getString("position"))
                .add(employee.getDouble("salary"))
                .add(userId)
                .add(status), // Add status here
            context, "Employee added successfully");
    } catch (Exception e) {
        context.response().setStatusCode(500).end("Failed to add employee: " + e.getMessage());
    }
}


public void updateEmployee(RoutingContext context) {
    String id = context.pathParam("id");
    JsonObject employee = context.getBodyAsJson();
    String status = employee.getString("status"); // Get the new status if provided

    dbClient.updateWithParams(
        "UPDATE employees SET name = ?, position = ?, salary = ?, user_id = ?, status = ? WHERE id = ?",
        new JsonArray()
            .add(employee.getString("name"))
            .add(employee.getString("position"))
            .add(employee.getDouble("salary"))
            .add(employee.getString("user_id"))
            .add(status)  // Update status here
            .add(id),
        res -> {
            if (res.succeeded()) {
                // Return JSON response instead of plain text
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("status", "success")
                        .put("message", "Employee updated successfully")
                        .encode());
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("status", "error")
                        .put("message", res.cause().getMessage())
                        .encode());
            }
        });
}

public void deleteEmployee(RoutingContext context) {
    String id = context.pathParam("id");

    // Ensure the ID is not null or invalid
    if (id == null || id.isEmpty()) {
        context.response()
            .setStatusCode(400)
            .end(new JsonObject().put("error", "Employee ID is required").encode());
        return;
    }

    // Delete the employee from the database
    dbClient.updateWithParams(
        "DELETE FROM employees WHERE id = ?",
        new JsonArray().add(id),
        res -> {
            if (res.succeeded()) {
                // Return a 204 No Content status code for successful deletion
                context.response().setStatusCode(204).end();
            } else {
                // Log the error for debugging purposes
                System.err.println("Error deleting employee with ID: " + id);
                res.cause().printStackTrace();

                // Return a 500 Internal Server Error status with the error message
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("status", "error")
                        .put("message", "Failed to delete employee")
                        .put("details", res.cause().getMessage())
                        .encode());
            }
        });
}
    
}