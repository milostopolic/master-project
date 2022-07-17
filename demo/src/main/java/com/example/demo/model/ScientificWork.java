package com.example.demo.model;

public class ScientificWork {

    private String category;

    private String title;

    public ScientificWork() {
    }

    public ScientificWork(String category, String title) {
        this.category = category;
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
