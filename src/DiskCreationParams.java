public class DiskCreationParams {
    final int bytesInSector; //11-12 bytes. Accept only 512, 1024, 2048 or 4096
    final int sectorsInCluster; //13 byte. Accept only power of 2, must be smaller that 32K
    final int reservedAreaInSector; //14-15 bytes.
    final int numberOfFATCopies; //16 byte - one copy is enough
    final int maxFilesInRoot; // 0 for FAT 32, 512 for FAT 16, power of 2 for FAT 12
    final long sectorsOnDisk; //19-20 bytes if fits (less that or equal to 0xFFFF). Otherwise, 32-35 bytes
    final int sectorsOnTrack; //24-25 bytes.
    final int sectorsBeforeStart; //28-31 bytes.

    private DiskCreationParams() {
        this.bytesInSector = 512;
        this.sectorsInCluster = 8;
        this.reservedAreaInSector = 0;
        this.numberOfFATCopies = 1;
        this.maxFilesInRoot = 0;
        this.sectorsOnDisk = 32;
        this.sectorsOnTrack = 4;
        this.sectorsBeforeStart = 0;
    }

    private DiskCreationParams(int bytesInSector, int sectorsInCluster, long sectorsOnDisk) {
        this.bytesInSector = bytesInSector;
        this.sectorsInCluster = sectorsInCluster;
        this.reservedAreaInSector = 0;
        this.numberOfFATCopies = 1;
        this.maxFilesInRoot = 0;
        this.sectorsOnDisk = sectorsOnDisk;
        this.sectorsOnTrack = 4;
        this.sectorsBeforeStart = 0;
    }

    static DiskCreationParams small() {
        return new DiskCreationParams(
                512,
                2,
                256
        );
    }
    static DiskCreationParams medium() {
        return new DiskCreationParams(
                512,
                2,
                16384
        );
    }
    static DiskCreationParams big() {
        return new DiskCreationParams(
                512,
                4,
                0x100000
        );
    }
    static DiskCreationParams custom(int bytesInSector, int sectorsInCluster, long sectorsOnDisk) {
        return new DiskCreationParams(
                bytesInSector,
                sectorsInCluster,
                sectorsOnDisk
        );
    }
}
