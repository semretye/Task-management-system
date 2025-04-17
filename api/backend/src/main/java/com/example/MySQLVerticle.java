package com.example;

import com.example.config.DatabaseConfig;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

public class MySQLVerticle extends AbstractVerticle {
    // private JDBCClient dbClient;
    private static JDBCClient sharedDbClient; // Static reference

    @Override
    public void start(Promise<Void> startPromise) {
            JsonObject config = new JsonObject()
            .put("url", DatabaseConfig.URL)
            .put("driver_class", DatabaseConfig.DRIVER)
            .put("user", DatabaseConfig.USER)
            .put("password", DatabaseConfig.PASSWORD)
            .put("max_pool_size", DatabaseConfig.MAX_POOL_SIZE);

        sharedDbClient = JDBCClient.createShared(vertx, config);

        sharedDbClient.getConnection(conn -> {
            if (conn.failed()) {
                System.out.println("Can not connect to DB");
                startPromise.fail(conn.cause());
                return;
            }

            SQLConnection connection = conn.result();
            createTables(connection)
                .onComplete(res -> {
                    connection.close();
                    if (res.succeeded()) {
                        startPromise.complete();
                    } else {
                        startPromise.fail(res.cause());
                    }
                });
        });
    }

    private Future<Void> createTables(SQLConnection connection) {
        return Future.future(promise -> connection.execute("""
               CREATE TABLE IF NOT EXISTS employees(
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                position VARCHAR(255) NOT NULL,
                salary DECIMAL(10,2) NOT NULL,
                status ENUM('active', 'inactive', 'on_leave') NOT NULL DEFAULT 'active',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
                """, ar -> {
                    if (ar.failed()) {
                        promise.fail(ar.cause());
                        return;
                    }
                    connection.execute("""
                         CREATE TABLE IF NOT EXISTS users(
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(255) UNIQUE NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        role ENUM('employee', 'manager', 'admin') NOT NULL DEFAULT 'employee'
                    )
                    """, promise);
                }));
    }

    public static JDBCClient getSharedDbClient() {
        return sharedDbClient;
    }
}