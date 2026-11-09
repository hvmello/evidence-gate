package dev.hvmello.evidencegate.adapter.audit;

import dev.hvmello.evidencegate.port.AuditLog;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryAuditLog implements AuditLog {

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void record(Entry entry) {
        entries.add(entry);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
