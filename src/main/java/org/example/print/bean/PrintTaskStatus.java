package org.example.print.bean;

public enum PrintTaskStatus {
    PENDING("待打印"),
    PRINTING("打印中"),
    FAILED("失败"),
    COMPLETED("完成");

    private final String description;

    PrintTaskStatus(String description) {
        this.description = description;
    }
}