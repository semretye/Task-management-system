package com.example;
import com.example.config.AuthConfig;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jdbc.JDBCClient;
import java.util.Base64;

public class MainVerticle extends AbstractVerticle {
   

    public static void main(String[] args) {
              
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.deployVerticle(new MySQLVerticle())
            .compose(id -> {
                JDBCClient dbClient = MySQLVerticle.getSharedDbClient();
                // JWTAuth jwtAuth = JWTAuth.create(vertx, 
                //     new JWTAuthOptions().setAlgorithm("HS256").setSecret(AuthConfig.JWT_SECRET)
                // );
                String encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(AuthConfig.JWT_SECRET.getBytes());

                JWTAuth jwtAuth = JWTAuth.create(vertx, 
                    new JWTAuthOptions().addJwk(new JsonObject()
                        .put("kty", "oct")
                        .put("k", encodedSecret) // Properly encoded secret key
                        .put("alg", "HS256") // Algorithm
                    )
                );
                return CompositeFuture.all(
                    vertx.deployVerticle(new AuthVerticle(dbClient, jwtAuth)),
                    vertx.deployVerticle(new EmployeeRoute(dbClient, jwtAuth))
                    // vertx.deployVerticle(new TaskRoute(dbClient, jwtAuth)),
                    // vertx.deployVerticle(new ProjectRoute(dbClient, jwtAuth))
                );
            })
            .onComplete(res -> startPromise.handle(res.mapEmpty()));
    }
}