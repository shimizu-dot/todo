package com.example.todo.model;

public enum TodoStatus {
    TODO("未着手"),
    IN_PROGRESS("進行中"),
    DONE("完了");

    private final String label;

    TodoStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
