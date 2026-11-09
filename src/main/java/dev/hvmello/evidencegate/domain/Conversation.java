package dev.hvmello.evidencegate.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** A conversation about one subject. Message ids are unique so a citation can point at exactly one message. */
public record Conversation(
        String subjectId, boolean consent, String patientName, String staffName, List<Message> messages) {

    public enum Speaker { PATIENT, STAFF }

    public static final int MAX_BODY_CHARS = 4_000;
    public static final int MAX_MESSAGES = 200;
    public static final int MAX_NAME_CHARS = 200;

    public record Message(int id, Speaker speaker, String body) {
        public Message {
            Objects.requireNonNull(speaker, "speaker");
            Objects.requireNonNull(body, "body");
            if (body.length() > MAX_BODY_CHARS) {
                throw new IllegalArgumentException("message body too long");
            }
        }
    }

    public Conversation {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        if (messages != null && messages.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("too many messages");
        }
        patientName = patientName == null ? "" : patientName;
        staffName = staffName == null ? "" : staffName;
        if (patientName.length() > MAX_NAME_CHARS || staffName.length() > MAX_NAME_CHARS) {
            throw new IllegalArgumentException("name too long");
        }
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        var ids = new HashSet<Integer>();
        for (var message : messages) {
            if (!ids.add(message.id())) {
                throw new IllegalArgumentException("duplicate message id: " + message.id());
            }
        }
    }
}
