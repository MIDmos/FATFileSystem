import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FATDirectory {
    final DirectoryEntry[] entries;

    private Set<String> deletion = new HashSet<>();
    private Set<String> appendingSet = new HashSet<>();
    private List<DirectoryEntry> appending = new LinkedList<>();
    private long selfDirCluster;
    private long parentDirCluster;
    private long newStartingCluster;

    public long getSelfDirCluster() {
        return selfDirCluster;
    }

    public DirectoryEntry getSelfDirEntry() {
        for (DirectoryEntry e : entries) {
            if (e.isSelfDir()) {
                return e;
            }
        }
        throw new IllegalStateException("Directory does not have a . reference");
    }

    public long getParentDirCluster() {
        return parentDirCluster;
    }

    public boolean isRoot() {
        return parentDirCluster == -1;
    }

    public void setSelfCluster(long newStartingCluster) {
        this.newStartingCluster = newStartingCluster;
    }

    public void markForDeletion(DirectoryEntry file) {
        deletion.add(file.fileNameWithExtension());
    }

    public void appendEntry(DirectoryEntry entry) {
        if (appendingSet.add(entry.fileNameWithExtension())) {
            appending.add(entry);
        }
    }

    private FATDirectory(DirectoryEntry[] entries) {
        this.entries = entries;
        for (DirectoryEntry e : entries) {
            if (!appendingSet.add(e.fileNameWithExtension())) {
                System.out.println("wrong name " + e.fileNameWithExtension() + "cluster = " + e.initialCluster);
            }
        }
        selfDirCluster = -1;
        parentDirCluster = -1;
        for (DirectoryEntry e : entries) {
            if (e.isSelfDir()) {
                selfDirCluster = e.initialCluster;
                newStartingCluster = selfDirCluster;
            }
            if (e.isParentDir()) {
                parentDirCluster = e.initialCluster;
            }
        }
        if (selfDirCluster == -1) {
            throw new IllegalArgumentException("Directory entries must contain . directory");
        }
    }

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate((entries.length - deletion.size() + appending.size()) * 32);
        Collections.addAll(appending, entries);
        HashSet<String> set = new HashSet<>();
        for (DirectoryEntry entry : appending) {
            if (!set.add(entry.fileNameWithExtension())) {
                System.out.println("Wrong name " + entry.fileNameWithExtension());
            }
            if (!deletion.contains(entry.fileNameWithExtension())) {
                buffer
                        .put(entry.name.getBytes(StandardCharsets.US_ASCII))
                        .put(entry.extension.getBytes(StandardCharsets.US_ASCII))
                        .put(entry.attribute)
                        .put(new byte[12])
                        .putInt((int) (entry.isSelfDir() && newStartingCluster > 0 ? newStartingCluster : entry.initialCluster))
                        .putInt((int) entry.size);
            }
        }
        appending.clear();
        deletion.clear();
        return buffer.array();
    }

    public static FATDirectory empty(long parentCluster) {
        byte attr = 0b10000;
        DirectoryEntry parentEntry = DirectoryEntry.directory("..", parentCluster);
        DirectoryEntry self = DirectoryEntry.directory(".", parentCluster);
        return new FATDirectory(new DirectoryEntry[]{self, parentEntry});
    }

    public static FATDirectory read(byte[] bytes) {
        List<DirectoryEntry> entries = new LinkedList<>();
        for (int entryStart = 0; entryStart < bytes.length; entryStart += 32) {
            if (entryStart + 32 < bytes.length) {
                boolean isEnded = true;
                for (byte b : Arrays.copyOfRange(bytes, entryStart, entryStart + 3)) {
                    if (b != 0) {
                        isEnded = false;
                        break;
                    }
                }
                if (isEnded) {
                    DirectoryEntry[] array = new DirectoryEntry[entries.size()];
                    for (int i = 0; i < array.length; i++) {
                        array[i] = entries.get(i);
                    }
                    return new FATDirectory(array);
                }
                ByteBuffer buffer = ByteBuffer.wrap(bytes, entryStart + 24, 8);
                entries.add(
                        new DirectoryEntry(
                                new String(Arrays.copyOfRange(bytes, entryStart, entryStart + 8), StandardCharsets.US_ASCII), //name
                                new String(Arrays.copyOfRange(bytes, entryStart + 8, entryStart + 11), StandardCharsets.US_ASCII), // ext
                                bytes[entryStart + 11], //attr
                                buffer.getInt(), //cluster
                                buffer.getInt() // size
                        ));
            }
        }
        DirectoryEntry[] array = new DirectoryEntry[entries.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = entries.get(i);
        }
        return new FATDirectory(array);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (DirectoryEntry e : entries) {
            builder.append(e.toString());
            builder.append('\n');
        }
        if (entries.length > 0) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }
}
