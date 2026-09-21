package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 装备结构版本迁移规则注册表。 */
public final class EquipmentStructureMigrations {
    // Registration and chain capture share one lock; author callbacks run outside it.
    private static final Map<MigrationKey, Entry> MIGRATIONS = new HashMap<>();

    private EquipmentStructureMigrations() {
    }

    /** Registers exactly one schema step. Duplicate source versions are rejected. */
    public static synchronized Registration register(
            ResourceLocation hostId,
            int fromVersion,
            int toVersion,
            EquipmentStructureMigration migration
    ) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(migration, "migration");
        if (fromVersion <= 0 || (long) toVersion != (long) fromVersion + 1) {
            throw new IllegalArgumentException("Migration must advance exactly one version: "
                    + fromVersion + " -> " + toVersion);
        }
        MigrationKey key = new MigrationKey(hostId, fromVersion);
        Entry entry = new Entry(toVersion, migration);
        if (MIGRATIONS.putIfAbsent(key, entry) != null) {
            throw new IllegalArgumentException("Migration already registered: " + key);
        }
        return new Registration(key, entry);
    }

    public static EquipmentStructure migrate(EquipmentStructure structure, int targetVersion) {
        Objects.requireNonNull(structure, "structure");
        if (targetVersion < structure.version()) {
            throw new IllegalArgumentException("Cannot migrate backwards from "
                    + structure.version() + " to " + targetVersion);
        }
        List<Entry> chain = captureChain(structure, targetVersion);
        EquipmentStructure current = structure;
        for (Entry entry : chain) {
            EquipmentStructure next = Objects.requireNonNull(
                    entry.migration.migrate(current), "migration result"
            );
            if (!current.hostId().equals(next.hostId())) {
                throw new IllegalArgumentException("A migration cannot change the host ID");
            }
            if (!current.equipmentType().equals(next.equipmentType())) {
                throw new IllegalArgumentException("A migration cannot change the equipment type");
            }
            if (next.version() != entry.toVersion) {
                throw new IllegalArgumentException("Migration result must match registered target version "
                        + entry.toVersion + ", got " + next.version());
            }
            current = next;
        }
        return current;
    }

    private static synchronized List<Entry> captureChain(EquipmentStructure structure, int targetVersion) {
        List<Entry> chain = new ArrayList<>();
        for (int version = structure.version(); version < targetVersion; version++) {
            Entry entry = MIGRATIONS.get(new MigrationKey(structure.hostId(), version));
            if (entry == null) {
                throw new IllegalStateException("Missing equipment structure migration: "
                        + structure.hostId() + " version " + version);
            }
            chain.add(entry);
        }
        return List.copyOf(chain);
    }

    /** Development/test lifecycle reset. Captured in-flight chains remain stable. */
    public static synchronized void clear() {
        MIGRATIONS.clear();
    }

    private record MigrationKey(ResourceLocation hostId, int fromVersion) {
    }

    // Identity, rather than callback equality, owns a registration across clear/re-register.
    private static final class Entry {
        private final int toVersion;
        private final EquipmentStructureMigration migration;

        private Entry(int toVersion, EquipmentStructureMigration migration) {
            this.toVersion = toVersion;
            this.migration = migration;
        }
    }

    public static final class Registration implements AutoCloseable {
        private final MigrationKey key;
        private final Entry entry;

        private Registration(MigrationKey key, Entry entry) {
            this.key = key;
            this.entry = entry;
        }

        public boolean unregister() {
            synchronized (EquipmentStructureMigrations.class) {
                return MIGRATIONS.remove(key, entry);
            }
        }

        public boolean isActive() {
            synchronized (EquipmentStructureMigrations.class) {
                return MIGRATIONS.get(key) == entry;
            }
        }

        @Override
        public void close() {
            unregister();
        }
    }
}
