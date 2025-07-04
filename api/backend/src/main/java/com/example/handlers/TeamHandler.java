package com.example.handlers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class TeamHandler {
    private final JDBCClient dbClient;

    public TeamHandler(JDBCClient dbClient) {
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

    public void createTeams(RoutingContext context) {
        try {
            JsonObject team = context.body().asJsonObject();
            System.out.println("Received team data: " + team.encodePrettily());
    
            // Validate required fields
            String name = team.getString("name");
            JsonArray members = team.getJsonArray("members");
    
            if (name == null || members == null) {
                context.response().setStatusCode(400)
                    .end(new JsonObject()
                        .put("error", "Missing required fields: name or members")
                        .encode());
                return;
            }
    
            // Start a transaction
            dbClient.getConnection(connRes -> {
                if (connRes.failed()) {
                    context.response().setStatusCode(500)
                        .end(new JsonObject()
                            .put("error", "Could not get database connection")
                            .encode());
                    return;
                }
    
                SQLConnection connection = connRes.result();
    
                // Start transaction
                connection.setAutoCommit(false, autoCommitRes -> {
                    // Insert the team
                    connection.updateWithParams(
                        "INSERT INTO teams(name) VALUES (?)",
                        new JsonArray().add(name),
                        insertTeamRes -> {
                            if (insertTeamRes.failed()) {
                                rollback(connection, context, "Failed to create team");
                                return;
                            }
    
                            Integer teamId = insertTeamRes.result().getKeys().getInteger(0);
                            AtomicInteger completed = new AtomicInteger(0);
                            int totalMembers = members.size();
    
                            if (totalMembers == 0) {
                                // No members to add, just commit
                                connection.commit(commitRes -> {
                                    connection.close();
                                    context.response()
                                        .setStatusCode(201)
                                        .end(new JsonObject()
                                            .put("message", "Team created with no members")
                                            .put("teamId", teamId)
                                            .encode());
                                });
                                return;
                            }
    
                            // Insert each member
                            for (int i = 0; i < totalMembers; i++) {
                                connection.updateWithParams(
                                    "INSERT INTO team_members(team_id, employee_id) VALUES (?, ?)",
                                    new JsonArray().add(teamId).add(members.getInteger(i)),
                                    insertMemberRes -> {
                                        if (insertMemberRes.failed()) {
                                            rollback(connection, context, "Failed to add team member");
                                            return;
                                        }
    
                                        // Check if all members are inserted
                                        if (completed.incrementAndGet() == totalMembers) {
                                            connection.commit(commitRes -> {
                                                connection.close();
                                                if (commitRes.failed()) {
                                                    context.response().setStatusCode(500)
                                                        .end(new JsonObject()
                                                            .put("error", "Failed to commit transaction")
                                                            .encode());
                                                } else {
                                                    context.response()
                                                        .setStatusCode(201)
                                                        .end(new JsonObject()
                                                            .put("message", "Team created successfully")
                                                            .put("teamId", teamId)
                                                            .encode());
                                                }
                                            });
                                        }
                                    });
                            }
                        });
                });
            });
        } catch (Exception e) {
            e.printStackTrace();
            context.response().setStatusCode(500)
                .end(new JsonObject()
                    .put("error", "Server error: " + e.getMessage())
                    .encode());
        }
    }
    

    public void getAllTeams(RoutingContext context) {
        dbClient.query("SELECT * FROM teams", res -> {
            if (res.succeeded()) {
                List<JsonObject> rows = res.result().getRows();
    
                // Process the teams and fetch members for each team
                JsonArray teamsArray = new JsonArray();
    
                for (JsonObject row : rows) {
                    Integer teamId = row.getInteger("id");
                    String teamName = row.getString("name");
    
                    // Get the members of the team
                    dbClient.queryWithParams(
                        "SELECT e.id, e.name FROM employees e " +
                        "JOIN team_members tm ON tm.employee_id = e.id " +
                        "WHERE tm.team_id = ?",
                        new JsonArray().add(teamId),
                        memberRes -> {
                            if (memberRes.succeeded()) {
                                JsonArray membersArray = new JsonArray();
                                for (JsonObject memberRow : memberRes.result().getRows()) {
                                    membersArray.add(new JsonObject()
                                            .put("id", memberRow.getInteger("id"))
                                            .put("name", memberRow.getString("name"))
                                    );
                                }
    
                                // Combine team with its members
                                JsonObject teamWithMembers = new JsonObject()
                                        .put("id", teamId)
                                        .put("name", teamName)
                                        .put("members", membersArray);
    
                                teamsArray.add(teamWithMembers);
                            } else {
                                context.response().setStatusCode(500).end(memberRes.cause().getMessage());
                                return;
                            }
    
                            // Only return teams when all member data is fetched
                            if (teamsArray.size() == rows.size()) {
                                context.response()
                                    .putHeader("Content-Type", "application/json")
                                    .end(teamsArray.encodePrettily());
                            }
                        }
                    );
                }
            } else {
                context.response().setStatusCode(500).end(res.cause().getMessage());
            }
        });
    }

    public void updateTeams(RoutingContext context) {
        String id = context.pathParam("id");
        JsonObject team = context.getBodyAsJson();
        
        dbClient.updateWithParams(
            "UPDATE teams SET name = ? WHERE id = ?",
            new JsonArray()
                .add(team.getString("name"))
                .add(id),
            res -> {
                if (res.succeeded()) {
                    context.response().setStatusCode(204).end();
                } else {
                    context.fail(res.cause());
                }
            }
        );
    }

    public void deleteTeams(RoutingContext context) {
        String id = context.pathParam("id");
        
        // First delete team members to maintain referential integrity
        dbClient.getConnection(connRes -> {
            if (connRes.failed()) {
                context.response().setStatusCode(500)
                    .end(new JsonObject()
                        .put("error", "Could not get database connection")
                        .encode());
                return;
            }

            SQLConnection connection = connRes.result();
            
            // Start transaction
            connection.setAutoCommit(false, autoCommitRes -> {
                // First delete team members
                connection.updateWithParams(
                    "DELETE FROM team_members WHERE team_id = ?",
                    new JsonArray().add(id),
                    deleteMembersRes -> {
                        if (deleteMembersRes.failed()) {
                            rollback(connection, context, "Failed to delete team members");
                            return;
                        }

                        // Then delete the team
                        connection.updateWithParams(
                            "DELETE FROM teams WHERE id = ?",
                            new JsonArray().add(id),
                            deleteTeamRes -> {
                                if (deleteTeamRes.failed()) {
                                    rollback(connection, context, "Failed to delete team");
                                    return;
                                }

                                connection.commit(commitRes -> {
                                    connection.close();
                                    if (commitRes.failed()) {
                                        context.response().setStatusCode(500)
                                            .end(new JsonObject()
                                                .put("error", "Failed to commit transaction")
                                                .encode());
                                    } else {
                                        context.response().setStatusCode(204).end();
                                    }
                                });
                            });
                    });
            });
        });
    }

    private void rollback(SQLConnection connection, RoutingContext context, String message) {
        connection.rollback(rollbackRes -> {
            connection.close();
            context.response().setStatusCode(500)
                .end(new JsonObject()
                    .put("error", message)
                    .encode());
        });
    }
}