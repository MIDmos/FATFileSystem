import java.io.*;
import java.nio.ByteBuffer;

public class BootRecord {

    static int FAT12_SIZE = 36;
    static int FAT16_SIZE = 36;
    static int FAT32_SIZE = 512;

    int bytesInSector; //11-12 bytes. Accept only 512, 1024, 2048 or 4096
    int sectorsInCluster; //13 byte. Accept only power of 2, must be smaller that 32K
    int reservedAreaInSector = 0; //14-15 bytes.
    int numberOfFATCopies = 1; //16 byte - 1 for FAT 16/12, 2 for FAT 32
    int maxFilesInRoot = 0; // 17-18 0 for FAT 32, 512 for FAT 16, power of 2 for FAT 12
    long sectorsOnDisk; //19-20 bytes if fits. Otherwise, 32-35 bytes (always 32-35)
    int sectorsInTrack; //24-25 bytes.
    int numberOfHeads = 1; //26-27 bytes.
    long sectorsBeforeStart = 0; //28-31 bytes

    int fat32_fatCopySize = 0; //36-39 bytes
    int fat32_fatUpdateMode = 0b10000000; // 40-41 bytes. Bit 7 = 1, only one copy of FAT is active
    int fat32_versionNumber = 0; // 42-43 bytes.
    int fat32_rootDirCluster = 2; // 44-47 bytes.

    int bytesInCluster() {
        return bytesInSector * sectorsInCluster;
    }

    long firstSectorOffset(){
        int bootSize = getSystemType() == FATType.FAT32 ? FAT32_SIZE : FAT16_SIZE;
        return bootSize + getFatSectionSizeInBytes();
    }

    long clustersOnDisk() {
        long full = sectorsOnDisk / sectorsInCluster;
        long last = sectorsOnDisk % sectorsInCluster;
        if (last != 0) {
            full++;
        }
        return full;
    }

    DataInputStream getFatSection(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        DataInputStream dis = new DataInputStream(fis);
        dis.skipBytes(getSystemType() == FATType.FAT32 ? FAT32_SIZE : FAT16_SIZE);
        return dis;
    }

    long getFatSectionSizeInBytes() {
        int multiplier = 0;
        switch (getSystemType()) {
            case FAT32:
                multiplier = 32;
                break;
            case FAT16:
                multiplier = 16;
                break;
            case FAT12:
                multiplier = 12;
                break;
        }
        return clustersOnDisk() / 8 * multiplier;
    }

    long getRootCluster() {
        if (getSystemType() == FATType.FAT32) {
            return fat32_rootDirCluster;
        }
        return 2;
    }

    FATType getSystemType() {
        long numberOfClusters = sectorsOnDisk / sectorsInCluster;
        if (numberOfClusters >= 0xFFF7) return FATType.FAT32;
        if (numberOfClusters >= 0xFF7) return FATType.FAT16;
        return FATType.FAT12;
    }

    private BootRecord(int bytesInSector, int sectorsInCluster, int numberOfFATCopies, int maxFilesInRoot, long sectorsOnDisk, int sectorsInTrack, int sectorsBeforeStart) {
        this.bytesInSector = bytesInSector;
        this.sectorsInCluster = sectorsInCluster;
        this.numberOfFATCopies = numberOfFATCopies;
        this.maxFilesInRoot = maxFilesInRoot;
        this.sectorsOnDisk = sectorsOnDisk;
        this.sectorsInTrack = sectorsInTrack;
        this.sectorsBeforeStart = Integer.toUnsignedLong(sectorsBeforeStart);
    }

    public BootRecord(DiskCreationParams params) {
        this(params.bytesInSector,
                params.sectorsInCluster,
                params.numberOfFATCopies,
                params.maxFilesInRoot,
                params.sectorsOnDisk,
                params.sectorsOnTrack,
                params.sectorsBeforeStart);
    }

    public static BootRecord read(FileInputStream fis) throws IOException {
        byte[] bytes = new byte[36];
        int read = fis.read(bytes);
        if (read < 36) {
            throw new IllegalArgumentException("Boot record is corrupted");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.get(new byte[11]);
        int bytesInSector = Short.toUnsignedInt(buffer.getShort());
        int sectorsInCluster = Byte.toUnsignedInt(buffer.get());
        buffer.getShort();
        int numberOfFATCopies = Byte.toUnsignedInt(buffer.get());
        int maxFilesInRoot = Short.toUnsignedInt(buffer.getShort());
        buffer.get(new byte[5]);
        int sectorsOnTrack = Short.toUnsignedInt(buffer.getShort());
        buffer.getShort();
        int sectorsBeforeStart = buffer.getInt();
        int sectorsOnDisk = buffer.getInt();

        return new BootRecord(
                bytesInSector,
                sectorsInCluster,
                numberOfFATCopies,
                maxFilesInRoot,
                sectorsOnDisk,
                sectorsOnTrack,
                sectorsBeforeStart
        );
    }

    public byte[] toByteArray() {
        FATType type = getSystemType();
        ByteBuffer buffer = ByteBuffer.allocate(type == FATType.FAT32 ? FAT32_SIZE : FAT16_SIZE);
        buffer.put(new byte[11]);
        buffer.putShort((short) bytesInSector);
        buffer.put((byte) sectorsInCluster);
        buffer.put(new byte[2]);
        buffer.put((byte) numberOfFATCopies);
        buffer.putShort((short) maxFilesInRoot);
        buffer.put(new byte[5]);
        buffer.putShort((short) sectorsInTrack);
        buffer.put(new byte[2]);
        buffer.putInt((int) sectorsBeforeStart);
        buffer.putInt((int) sectorsOnDisk);
        if (type != FATType.FAT32) {
            return buffer.array();
        }
        buffer.putInt(fat32_fatCopySize);
        buffer.putShort((short) fat32_fatUpdateMode);
        buffer.putShort((short) fat32_versionNumber);
        buffer.putInt(fat32_rootDirCluster);
        return buffer.array();
    }
}
