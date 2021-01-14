import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DirectoryEntry {
    final String name; // 0-7 00h - last entry 05h, E5h - has been erased and is available 2Eh - dot
    final String extension; // 8-10
    final byte attribute; // 11 10000b – Directory
    final long initialCluster;
    final long size; // zero for dir

    public DirectoryEntry(String name, String extension, byte attribute, int initialCluster, int size) {
        byte[] nameBytes = new byte[8];
        Arrays.fill(nameBytes, (byte) 0x20);
        byte[] nBytes = name.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < nameBytes.length && i < nBytes.length; i++) {
            nameBytes[i] = nBytes[i];
        }
        this.name = new String(nameBytes, StandardCharsets.US_ASCII);

        byte[] extBytes = new byte[3];
        Arrays.fill(extBytes, (byte) 0x20);
        byte[] eBytes = extension.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < extBytes.length && i < eBytes.length; i++) {
            extBytes[i] = eBytes[i];
        }
        this.extension = new String(extBytes, StandardCharsets.US_ASCII);

        this.attribute = attribute;
        this.initialCluster = Integer.toUnsignedLong(initialCluster);
        this.size = Integer.toUnsignedLong(size);
    }

    public static DirectoryEntry directory(String name, long initialCluster) {
        byte attr = 0b10000;
        return new DirectoryEntry(name, "", attr, (int) initialCluster, 0);
    }

    public static DirectoryEntry file(String name, String extension, long initialCluster, int size) {
        byte attr = 0;
        return new DirectoryEntry(name, extension, attr, (int)initialCluster, size);
    }

    public boolean isDir() {
        return attribute == 0b10000;
    }

    public boolean isSelfDir() {
        return name.trim().equals(".");
    }

    public boolean isParentDir() {
        return name.trim().equals("..");
    }

    public String fileNameWithExtension() {
        if (extension.trim().isEmpty()) {
            return name.trim();
        }
        return name.trim() + "." + extension.trim();
    }

    @Override
    public String toString() {
        return "name = " + fileNameWithExtension() + ", attr = " + attribute + ", isDir = " + isDir() + ", size = " + size;
    }
}
