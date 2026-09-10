package com.cqu.service;

import com.cqu.model.Node;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeRepository {
    private final EntityManagerFactory emf;

    public NodeRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public boolean hasAnyNodes() {
        EntityManager em = emf.createEntityManager();
        try {
            Long count = em.createQuery("select count(n) from Node n", Long.class).getSingleResult();
            return count != null && count > 0;
        } finally {
            em.close();
        }
    }

    public void saveAll(List<Node> nodes) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            for (Node n : nodes) {
                em.merge(n);
            }
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Node> findAll() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("select n from Node n", Node.class).getResultList();
        } finally {
            em.close();
        }
    }

    public Map<String, Node> findAllAsMap() {
        List<Node> nodes = findAll();
        Map<String, Node> map = new HashMap<>();
        for (Node n : nodes) {
            map.put(n.getId(), n);
        }
        return map;
    }
}

