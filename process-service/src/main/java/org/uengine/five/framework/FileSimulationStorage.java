package org.uengine.five.framework;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.modeling.resource.Serializer;

/** File persistence for one simulation and its child instances and work items. */
public final class FileSimulationStorage implements AutoCloseable {
    // The range is disjoint from ordinary instance IDs and exactly representable by JavaScript Number.
    private static final long FILE_ID_START = 8_000_000_000_000_000L;
    private static final long FILE_ID_END = 9_000_000_000_000_000L;
    private static final int FILE_ID_BLOCK_SIZE = 1_000_000;
    private static final DateTimeFormatter INSTANCE_DAY = DateTimeFormatter.ofPattern("uuuu/MM/dd");
    private static final int RUNS_PER_DAY = 10_000;
    private static final SecureRandom FILE_IDS = new SecureRandom();
    private static final ReentrantLock[] FILE_LOCKS = createFileLocks();

    static boolean ownsId(Long id) {
        return id != null && id >= FILE_ID_START && id < FILE_ID_END;
    }

    private static long rootOf(long id) {
        return id - (id - FILE_ID_START) % FILE_ID_BLOCK_SIZE;
    }

    static FileSimulationStorage create(String owner) throws Exception {
        LocalDate day = LocalDate.now();
        Path directory = Files.createDirectories(dayDirectory(day));
        long dayBase = FILE_ID_START + day.toEpochDay() * RUNS_PER_DAY * FILE_ID_BLOCK_SIZE;
        for (int i = 0; i < 20; i++) {
            long id = dayBase + FILE_IDS.nextInt(RUNS_PER_DAY) * (long) FILE_ID_BLOCK_SIZE;
            if (!ownsId(id)) throw new IOException("File simulation ID date is out of range");
            try {
                Files.createFile(lockPath(directory, id));
            } catch (FileAlreadyExistsException alreadyUsed) {
                continue;
            }
            return new FileSimulationStorage(id, directory, true, owner);
        }
        throw new IOException("Could not allocate an unused file simulation ID");
    }

    static FileSimulationStorage open(long id, boolean writable) throws Exception {
        long rootId = rootOf(id);
        long epochDay = (rootId - FILE_ID_START) / FILE_ID_BLOCK_SIZE / RUNS_PER_DAY;
        return new FileSimulationStorage(rootId, dayDirectory(LocalDate.ofEpochDay(epochDay)), writable, null);
    }

    boolean contains(long id) {
        return rootId == rootOf(id);
    }

    private static Path dayDirectory(LocalDate day) {
        var applicationContext = ProcessServiceApplication.getApplicationContext();
        String configured = applicationContext == null ? System.getProperty("uengine.definition.basePath")
                : applicationContext.getEnvironment().getProperty("uengine.definition.basePath");
        Path base = Path.of(configured == null || configured.isBlank() ? "." : configured);
        return base.toAbsolutePath().normalize().resolve("definition/simulation/instances")
                .resolve(day.format(INSTANCE_DAY));
    }

    private static Path lockPath(Path directory, long rootId) {
        return directory.resolve("." + rootId + ".lock");
    }

    private static Path findSnapshot(Path directory, long rootId) throws IOException {
        Path snapshot = null;
        String prefix = rootId + "_";
        try (var files = Files.newDirectoryStream(directory, prefix + "*")) {
            for (Path candidate : files) {
                String timestamp = candidate.getFileName().toString().substring(prefix.length());
                if (!timestamp.matches("[0-9]+") || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
                if (snapshot != null) throw new IOException("Multiple snapshots for simulation " + rootId);
                snapshot = candidate;
            }
        }
        return snapshot;
    }

    private static ReentrantLock[] createFileLocks() {
        ReentrantLock[] locks = new ReentrantLock[256];
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
        return locks;
    }

    private static final class FileSnapshot {
        private final String owner;
        private final int nextOffset;
        private final Map<Long, ProcessInstanceEntity> instances;
        private final Map<Long, WorklistEntity> workItems;
        private final Map<Long, String> definitionXml;

        private FileSnapshot(String owner, int nextOffset, Map<Long, ProcessInstanceEntity> instances,
                Map<Long, WorklistEntity> workItems, Map<Long, String> definitionXml) {
            this.owner = owner;
            this.nextOffset = nextOffset;
            this.instances = instances;
            this.workItems = workItems;
            this.definitionXml = definitionXml;
        }
    }

    private final long rootId;
    private final Path directory;
    private final Path snapshotPath;
    private final boolean writable;
    private final String owner;
    private final Map<Long, ProcessInstanceEntity> instances = new LinkedHashMap<>();
    private final Map<Long, WorklistEntity> workItems = new LinkedHashMap<>();
    private final Map<Long, String> definitionXml = new LinkedHashMap<>();
    private ReentrantLock localLock;
    private FileChannel lockChannel;
    private FileLock fileLock;
    private int nextOffset;
    private boolean dirty;
    private boolean deleted;

    private FileSimulationStorage(long rootId, Path directory, boolean writable, String newOwner) throws Exception {
        this.rootId = rootId;
        this.directory = directory;
        this.writable = writable;
        String loadedOwner = null;
        Path snapshot = null;
        Path lock = lockPath(directory, rootId);
        if (Files.isRegularFile(lock, LinkOption.NOFOLLOW_LINKS)) {
            try {
                if (writable) {
                    localLock = FILE_LOCKS[(int) ((rootId - FILE_ID_START) / FILE_ID_BLOCK_SIZE % FILE_LOCKS.length)];
                    localLock.lock();
                    lockChannel = FileChannel.open(lock, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    fileLock = lockChannel.lock();
                }
                snapshot = findSnapshot(directory, rootId);
                if (snapshot != null) {
                    try (var input = Files.newInputStream(snapshot, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                        FileSnapshot record = (FileSnapshot) Serializer.deserialize(input);
                        loadedOwner = record.owner;
                        nextOffset = record.nextOffset;
                        instances.putAll(record.instances);
                        workItems.putAll(record.workItems);
                        definitionXml.putAll(record.definitionXml);
                    }
                } else if (newOwner != null) {
                    snapshot = directory.resolve(rootId + "_" + System.currentTimeMillis());
                }
            } catch (Exception e) {
                try {
                    close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
                throw e;
            }
        }
        snapshotPath = snapshot;
        owner = newOwner != null ? newOwner : loadedOwner;
        if (newOwner != null) nextOffset = 1;
    }

    public String getOwner() {
        return owner;
    }

    public void saveInstance(ProcessInstanceEntity instance) {
        if (deleted) return;
        ensureWritable();
        if (instance.getInstId() == null) {
            instance.setInstId(instances.isEmpty() ? rootId : nextId());
        }
        ensureLocalId(instance.getInstId());
        instances.put(instance.getInstId(), instance);
        dirty = true;
    }

    public Optional<ProcessInstanceEntity> findInstance(Long instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    public void saveDefinitionXml(Long instanceId, String xml) {
        ensureWritable();
        ensureLocalId(instanceId);
        definitionXml.put(instanceId, xml);
        dirty = true;
    }

    public String getDefinitionXml(Long instanceId) {
        return definitionXml.get(instanceId);
    }

    public void saveWorkItem(WorklistEntity item) {
        ensureWritable();
        if (item.getTaskId() == null) item.setTaskId(nextId());
        ensureLocalId(item.getTaskId());
        if (item.getInstId() != null) ensureLocalId(item.getInstId());
        if (item.getRootInstId() != null) ensureLocalId(item.getRootInstId());
        workItems.put(item.getTaskId(), item);
        dirty = true;
    }

    public Optional<WorklistEntity> findWorkItem(Long taskId) {
        return Optional.ofNullable(workItems.get(taskId)).map(this::attachInstance);
    }

    public List<WorklistEntity> findWorkItems(Long rootOrInstanceId) {
        return workItems.values().stream()
                .filter(item -> rootOrInstanceId.equals(item.getRootInstId()) || rootOrInstanceId.equals(item.getInstId()))
                .map(this::attachInstance)
                .toList();
    }

    private WorklistEntity attachInstance(WorklistEntity item) {
        item.setProcessInstance(instances.get(item.getInstId()));
        return item;
    }

    public void delete() {
        ensureWritable();
        deleted = true;
        dirty = true;
    }

    private long nextId() {
        if (nextOffset <= 0 || nextOffset >= FILE_ID_BLOCK_SIZE) {
            throw new IllegalStateException("File simulation ID block is exhausted");
        }
        return rootId + nextOffset++;
    }

    private void ensureLocalId(long id) {
        if (id < rootId || id >= rootId + FILE_ID_BLOCK_SIZE) {
            throw new IllegalArgumentException("Entity is outside the file simulation");
        }
    }

    private void ensureWritable() {
        if (!writable || fileLock == null || owner == null || deleted) {
            throw new IllegalStateException("File simulation is not writable");
        }
    }

    void commit() throws Exception {
        if (!dirty) return;
        if (deleted) {
            Files.deleteIfExists(snapshotPath);
            return;
        }
        Path temporary = Files.createTempFile(directory, ".simulation-", ".tmp");
        try {
            Serializer.serialize(new FileSnapshot(owner, nextOffset, instances, workItems, definitionXml),
                    Files.newOutputStream(temporary, StandardOpenOption.WRITE));
            try (var file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                file.force(true);
            }
            Files.move(temporary, snapshotPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void close() throws IOException {
        FileChannel channel = lockChannel;
        ReentrantLock lock = localLock;
        lockChannel = null;
        localLock = null;
        fileLock = null;
        try {
            if (channel != null) channel.close();
        } finally {
            if (lock != null) lock.unlock();
        }
    }
}
