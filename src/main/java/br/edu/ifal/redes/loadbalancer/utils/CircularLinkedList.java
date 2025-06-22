package br.edu.ifal.redes.loadbalancer.utils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CircularLinkedList<T> {

    private final LinkedList<T> list = new LinkedList<>();

    // TODOS estes métodos devem ser synchronized
    public synchronized void add(T item) {
        if (!list.contains(item)) {
            list.add(item);
        }
    }

    public synchronized boolean contains(T item) {
        return list.contains(item);
    }

    public synchronized void remove(T item) {
        list.remove(item);
    }

    public synchronized int size() {
        return list.size();
    }

    public synchronized T next() { 
        if (list.isEmpty()) {
            return null;
        }
        final T item = list.removeFirst();
        list.addLast(item);
        return item;
    }

    public synchronized List<T> getList() { // Retorna uma cópia para iteração segura
        return Collections.unmodifiableList(new LinkedList<>(list)); 
    }
}