package com.homecloud.backend.dto;

public class BreadcrumbItem {

    private Long id;
    private String name;

    public BreadcrumbItem() {}

    public BreadcrumbItem(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
