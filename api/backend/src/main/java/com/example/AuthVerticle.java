package com.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.List;
import java.util.Set;

public class AuthVerticle extends AbstractVerticle {
    private JDBCClient dbClient;
    private JWTAuth jwtAuth;

    public AuthVerticle(JDBCClient dbClient, JWTAuth jwtAuth) {
        this.dbClient = dbClient;
        this.jwtAuth = jwtAuth;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // CORS and preflight OPTIONS handling
        router.route().handler(CorsHandler.create("*")
            .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.OPTIONS))
            .allowedHeaders(Set.of("Content-Type", "Authorization"))
            .exposedHeaders(Set.of("Authorization")));

        router.options().handler(ctx -> {
            ctx.response()
               .setStatusCode(204)
               .putHeader("Access-Control-Allow-Origin", "*")
               .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
               .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
               .end();
        });

        router.route().handler(BodyHandler.create());

        // Auth Endpoints
        router.post("/api/login").handler(this::handleLogin);
        router.post("/api/register").handler(this::handleRegister);
        router.patch("/api/assign_roles/:id/role").handler(this::assignRoles);

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080) // Changed from 8081 to 8080
            .onSuccess(server -> startPromise.complete())
            .onFailure(startPromise::fail);
    }

    private void handleLogin(RoutingContext ctx) {
        JsonObject credentials = ctx.getBodyAsJson();
        String username = credentials.getString("username");
        String password = credentials.getString("password");
    
        String query = "SELECT u.*, e.id AS employeeId, e.name AS employeeName " +
                       "FROM users u LEFT JOIN employees e ON u.id = e.user_id " +
                       "WHERE u.username = ?";
    
        dbClient.queryWithParams(query, new JsonArray().add(username), res -> {
            if (res.failed()) {
                sendErrorResponse(ctx, 500, "Database error");
                return;
            }
    
            if (res.result().getRows().isEmpty()) {
                sendErrorResponse(ctx, 401, "Invalid credentials");
                return;
            }
    
            JsonObject user = res.result().getRows().get(0);
            if (BCrypt.verifyer().verify(password.toCharArray(), user.getString("password")).verified) {
                String token = jwtAuth.generateToken(
                    new JsonObject()
                        .put("sub", user.getString("username"))
                        .put("role", user.getString("role")),
                    new JWTOptions().setExpiresInSeconds(3600)
                );
    
                JsonObject response = new JsonObject()
                    .put("token", token)
                    .put("userId", user.getInteger("id"))
                    .put("role", user.getString("role"))
                    .put("employeeId", user.getValue("employeeId"))  // can be null if not an employee
                    .put("employeeName", user.containsKey("employeeName") ? user.getString("employeeName") : "");

                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Access-Control-Allow-Origin", "*")
                    .putHeader("Authorization", "Bearer " + token)
                    .end(response.encode());
            } else {
                sendErrorResponse(ctx, 401, "Invalid credentials");
            }
        });
    }
    

    private void handleRegister(RoutingContext ctx) {
        try {
            JsonObject registrationData = ctx.getBodyAsJson();
    
            // Check for missing required fields
            if (!registrationData.containsKey("username") ||
                !registrationData.containsKey("password") ||
                !registrationData.containsKey("name") ||
                !registrationData.containsKey("position") ||
                !registrationData.containsKey("salary") ||
                !registrationData.containsKey("status")) {  // Check if status is provided
                sendErrorResponse(ctx, 400, "Missing required fields");
                return;
            }
    
            String hashedPassword = BCrypt.withDefaults().hashToString(12, registrationData.getString("password").toCharArray());
    
            // Execute database operations in a transaction
            vertx.executeBlocking(promise -> {
                dbClient.getConnection(connRes -> {
                    if (connRes.failed()) {
                        promise.fail(connRes.cause());
                        return;
                    }
    
                    SQLConnection connection = connRes.result();
                    connection.setAutoCommit(false, autoCommitRes -> {
                        // Insert into 'users' table
                        connection.updateWithParams(
                            "INSERT INTO users(username, password, role) VALUES (?, ?, ?)",
                            new JsonArray()
                                .add(registrationData.getString("username"))
                                .add(hashedPassword)
                                .add(registrationData.getString("role", "employee")),  // Default role is 'employee'
                            userRes -> {
                                if (userRes.failed()) {
                                    rollback(connection, promise, userRes.cause().getMessage().contains("Duplicate") ?
                                            "Username already exists" : "Failed to create user");
                                    return;
                                }
    
                                String userId = userRes.result().getKeys().getString(0);
    
                                // Insert into 'employees' table with the new status field
                                connection.updateWithParams(
                                    "INSERT INTO employees(name, position, salary, user_id, status) VALUES (?, ?, ?, ?, ?)",
                                    new JsonArray()
                                        .add(registrationData.getString("name"))
                                        .add(registrationData.getString("position"))
                                        .add(registrationData.getDouble("salary"))
                                        .add(userId)
                                        .add(registrationData.getString("status")),  // Add status
                                    employeeRes -> {
                                        if (employeeRes.succeeded()) {
                                            connection.commit(commitRes -> {
                                                connection.close();
                                                if (commitRes.succeeded()) {
                                                    JsonObject response = new JsonObject()
                                                        .put("success", true)
                                                        .put("userId", userId)
                                                        .put("employeeId", employeeRes.result().getKeys().getString(0));
                                                    promise.complete(response);
                                                } else {
                                                    promise.fail("Commit failed");
                                                }
                                            });
                                        } else {
                                            rollback(connection, promise, "Failed to create employee");
                                        }
                                    });
                            });
                    });
                });
            }, false, res -> {
                if (res.succeeded()) {
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .putHeader("Access-Control-Allow-Origin", "*")
                        .end(res.result().toString());
                } else {
                    sendErrorResponse(ctx, 400, res.cause().getMessage());
                }
            });
        } catch (Exception e) {
            sendErrorResponse(ctx, 500, "Internal server error");
        }
    }
    
    private void rollback(SQLConnection connection, Promise<Object> promise, String message) {
        connection.rollback(rollbackRes -> {
            connection.close();
            promise.fail(message);
        });
    }

    private void sendErrorResponse(RoutingContext context, int statusCode, String message) {
        context.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .putHeader("Access-Control-Allow-Origin", "*")
            .end(new JsonObject().put("error", message).encode());
    }

    public void assignRoles(RoutingContext context) {
        String id = context.pathParam("id");
        JsonObject user = context.getBodyAsJson();
        String newRole = user.getString("role");

        Set<String> allowedRoles = Set.of("employee", "admin", "manager");
        if (newRole == null || !allowedRoles.contains(newRole)) {
            sendErrorResponse(context, 400, "Invalid role. Allowed roles: " + allowedRoles);
            return;
        }

        dbClient.updateWithParams(
            "UPDATE users SET role = ? WHERE id = ?",
            new JsonArray().add(newRole).add(id),
            res -> {
                if (res.succeeded()) {
                    context.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .putHeader("Access-Control-Allow-Origin", "*")
                        .end(new JsonObject().put("message", "Role updated successfully").encode());
                } else {
                    sendErrorResponse(context, 500, "Failed to update role");
                }
            });
    }
}
