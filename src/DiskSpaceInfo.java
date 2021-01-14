public class DiskSpaceInfo {
    String systemName;
    long allBytes;
    long freeBytes;
    long usedBytes;

    public DiskSpaceInfo(String systemName,long allBytes, long freeBytes, long usedBytes) {
        this.systemName = systemName;
        this.allBytes = allBytes;
        this.freeBytes = freeBytes;
        this.usedBytes = usedBytes;
    }

    @Override
    public String toString() {
        return "DiskSpaceInfo{" +
                "system=" + systemName +
                ", allBytes=" + allBytes +
                ", freeBytes=" + freeBytes +
                ", usedBytes=" + usedBytes +
                '}';
    }
}
