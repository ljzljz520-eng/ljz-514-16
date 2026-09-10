package com.cqu.service;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

public final class Db {
    private final EntityManagerFactory emf;

    public Db() {
        this.emf = initEntityManagerFactory();
    }

    public EntityManagerFactory emf() {
        return emf;
    }

    public void close() {
        emf.close();
    }

    private static EntityManagerFactory initEntityManagerFactory() {
        Map<String, Object> props = new HashMap<>();

        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String name = System.getenv().getOrDefault("DB_NAME", "cq_travel");
        String user = System.getenv().getOrDefault("DB_USER", "cq");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "cq");
        String ddlAuto = System.getenv().getOrDefault("HIBERNATE_DDL_AUTO", "update");

        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        props.put("jakarta.persistence.jdbc.url", "jdbc:postgresql://" + host + ":" + port + "/" + name);
        props.put("jakarta.persistence.jdbc.user", user);
        props.put("jakarta.persistence.jdbc.password", password);

        props.put("hibernate.hbm2ddl.auto", ddlAuto);
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.show_sql", "false");
        props.put("hibernate.format_sql", "false");

        return Persistence.createEntityManagerFactory("cq-travel", props);
    }
}

