package com.fileserver;

public class FileRequest {
    private final String id;
    private final String description;
    private final String requester;
    private final String recipient;

    public FileRequest(String id, String description, String requester, String recipient) {
        this.id = id;
        this.description = description;
        this.requester = requester;
        this.recipient = recipient;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getRequester() {
        return requester;
    }

    public String getRecipient() {
        re