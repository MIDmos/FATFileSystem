import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public class FileAllocationTable {

    private static final int FAT_TYPE_12 = 0;
    private static final int FAT_TYPE_16 = 1;
    private static final int FAT_TYPE_32 = 2;

    private static final int[] clusterSize = {12, 16, 32};

    private static final int[] availableCodeSet = {0, 0, 0};
    private static final int[] reservedCodeSet = {1, 1, 1};
    private static final long[] badClusterSet = {0xFF7, 0xFFF7, 0x0FFFFFF7};
    private static final long[] endMarkerMinSet = {0xFF8, 0xFFF8, 0x0FFFFFF8};
    private static final long[] endMarkerMaxSet = {0xFFF, 0xFFFF, 0x0FFFFFFF};

    private final int availableCode;
    private final int reservedCode;
    private final long badCluster;
    private final long endMarkerMin;
    private final long endMarkerMax;

    private final long[][] table;
    private final long tableSize;
    private List<Long> freeClusters = new LinkedList<>();

    private FileAllocationTable(int availableCode, int reservedCode, long badCluster, long endMarkerMin, long endMarkerMax, long[][] table) {
        this.availableCode = availableCode;
        this.reservedCode = reservedCode;
        this.badCluster = badCluster;
        this.endMarkerMin = endMarkerMin;
        this.endMarkerMax = endMarkerMax;
        long size = 0;
        for (long[] longs : table) {
            size += longs.length;
        }
        tableSize = size;
        this.table = table;
        for (long i = 2; i < tableSize; i++) {
            if (getTableValue(i) == availableCode) {
                freeClusters.add(i);
            }
        }
    }

    private static FileAllocationTable createTableWithSource(FATType fatType, long[][] table) {
        int fatIndex = getFATTypeIndex(fatType);
        return new FileAllocationTable(
                availableCodeSet[fatIndex],
                reservedCodeSet[fatIndex],
                badClusterSet[fatIndex],
                endMarkerMinSet[fatIndex],
                endMarkerMaxSet[fatIndex],
                table
        );
    }

    private static int getFATTypeIndex(FATType type) {
        switch (type) {
            case FAT12:
                return FAT_TYPE_12;
            case FAT16:
                return FAT_TYPE_16;
            case FAT32:
                return FAT_TYPE_32;
        }
        throw new IllegalStateException("Unknown FAT type " + type);
    }

    public static FileAllocationTable read(FATType fatType, InputStream stream, long clustersCount) throws IOException {
        int fatIndex = getFATTypeIndex(fatType);
        int size = (int) (clustersCount / Integer.MAX_VALUE);
        int last = (int) (clustersCount % Integer.MAX_VALUE);
        if (last > 0) {
            size++;
        }
        long[][] table = new long[size][];
        for (int i = 0; i < size; i++) {
            if (i != size - 1 || last == 0) {
                table[i] = new long[Integer.MAX_VALUE];
            } else {
                table[i] = new long[last];
            }
            if (fatType == FATType.FAT12) {
                for (int j = 0; j < table[i].length; j += 2) {
                    byte[] bytes = new byte[3];
                    int read = stream.read(bytes);
                    if (read != 3) {
                        throw new IllegalStateException("Wrong input stream");
                    }
                    table[i][j] = (
                            ((short) bytes[0] & 0xff) << 4 |
                                    ((short) bytes[1] >> 4 & 0xf)
                    );
                    table[i][j + 1] = (
                            ((short) bytes[1] & 0x0f) << 8 |
                                    ((short) bytes[2] & 0xff)
                    );
                }
            } else {
                for (int j = 0; j < table[i].length; j++) {
                    if (fatType == FATType.FAT16) {
                        byte[] bytes = new byte[2];
                        int read = stream.read(bytes);
                        if (read != 2) {
                            throw new IllegalStateException("Wrong input stream");
                        }
                        table[i][j] = (
                                ((short) bytes[0] & 0xff) << 8 |
                                        ((short) bytes[1] & 0xff)
                        );
                    } else {
                        byte[] bytes = new byte[4];
                        int read = stream.read(bytes);
                        if (read != 4) {
                            throw new IllegalStateException("Wrong input stream");
                        }
                        table[i][j] = (
                                ((int) bytes[0] & 0xff) << 24 |
                                        ((int) bytes[1] & 0xff) << 16 |
                                        ((int) bytes[2] & 0xff) << 8 |
                                        ((int) bytes[3] & 0xff)
                        );
                    }
                }
            }
        }
        return createTableWithSource(fatType, table);
    }

    public static FileAllocationTable createTableWithSize(FATType fatType, long tableSize, long rootCluster) {
        int sizeI = (int) (tableSize / Integer.MAX_VALUE);
        int last = (int) (tableSize % Integer.MAX_VALUE);
        if (last > 0) {
            sizeI++;
        }
        long[][] table = new long[sizeI][];
        for (int i = 0; i < sizeI; i++) {
            if (i != sizeI - 1 || last == 0) {
                table[i] = new long[Integer.MAX_VALUE];
            } else {
                table[i] = new long[last];
            }
        }
        int fatIndex = getFATTypeIndex(fatType);
        FileAllocationTable result = new FileAllocationTable(
                availableCodeSet[fatIndex],
                reservedCodeSet[fatIndex],
                badClusterSet[fatIndex],
                endMarkerMinSet[fatIndex],
                endMarkerMaxSet[fatIndex],
                table
        );
        result.setTableValue(rootCluster, endMarkerMaxSet[fatIndex]);
        result.freeClusters.remove(rootCluster);
        return result;
    }

    public int getAvailableClustersCount() {
        return freeClusters.size();
    }

    public boolean canPutClusters(int count) {
        return freeClusters.size() >= count;
    }

    public long[] updateClusterSequence(int count, long startingCluster) {
        long[] current = getFileSequenceWithStartingCluster(startingCluster);
        if (count == current.length) {
            return current;
        }
        long[] resulting = new long[count];
        if (count > current.length) {
            int diff = count - current.length;
            if (freeClusters.size() < diff) {
                throw new IllegalStateException("Available space is not enough to update " + count + " clusters");
            }
            long[] appending = putClusters(diff);
            setTableValue(current[current.length - 1], endMarkerMax);
            System.arraycopy(current, 0, resulting, 0, current.length);
            System.arraycopy(appending, 0, resulting, current.length, appending.length);
            return resulting;
        } else {
            System.arraycopy(current, 0, resulting, 0, resulting.length);
            setTableValue(resulting[resulting.length - 1], endMarkerMax);
            deleteClustersFrom(current[resulting.length]);
            return resulting;
        }
    }

    public long[] putClusters(int count) {
        if (count < 0) {
            return new long[]{};
        }
        if (freeClusters.size() < count) {
            throw new IllegalStateException("Available space is not enough to store " + count + " clusters");
        }
        List<Long> allocated = freeClusters.subList(0, count);
        List<Long> free = freeClusters.subList(count, freeClusters.size());
        setTableValue(allocated.get(allocated.size() - 1), endMarkerMax);
        for (int i = allocated.size() - 2; i >= 0; i--) {
            setTableValue(allocated.get(i), allocated.get(i + 1));
        }
        freeClusters = free;
        return allocated.stream().mapToLong(v -> v).toArray();
    }

    public void deleteClusters(long[] clusters) {
        for (long c : clusters) {
            if (getTableValue(c) != availableCode) {
                setTableValue(c, availableCode);
                freeClusters.add(c);
            }
        }
    }

    public void deleteClustersFrom(long startingCluster) {
        long[] clusters = getFileSequenceWithStartingCluster(startingCluster);
        for (long c : clusters) {
            if (getTableValue(c) != availableCode) {
                setTableValue(c, availableCode);
                freeClusters.add(c);
            }
        }
    }

    public long[] getFileSequenceWithStartingCluster(long startingCluster) {
        List<Long> fileSequence = new LinkedList<>();
        fileSequence.add(startingCluster);
        long currentCode = getTableValue(startingCluster);
        while (clusterCodeIsData(currentCode)) {
            fileSequence.add(currentCode);
            currentCode = getTableValue(currentCode);
        }
        if (isCodeFileEnd(currentCode))
            return fileSequence.stream().mapToLong(v -> v).toArray();
        if (currentCode == badCluster)
            throw new IllegalStateException("File is corrupted at cluster " + fileSequence.get(fileSequence.size() - 1));
        throw new IllegalStateException("File sequence is broken at cluster " + fileSequence.get(fileSequence.size() - 1));
    }

    private boolean clusterCodeIsData(long code) {
        return code > reservedCode && code < badCluster;
    }

    private boolean isCodeFileEnd(long code) {
        return (code >= endMarkerMin && code <= endMarkerMax);
    }

    private void setTableValue(long cluster, long value) {
        table[(int) cluster / Integer.MAX_VALUE][(int) cluster % Integer.MAX_VALUE] = value;
    }

    private long getTableValue(long cluster) {
        return table[(int) cluster / Integer.MAX_VALUE][(int) cluster % Integer.MAX_VALUE];
    }

    public void write(FATType type, DataOutput stream) throws IOException {
        if (type == FATType.FAT12) {
            for (long[] la : table) {
                for (int i = 0; i < la.length; i += 2) {
                    long l1 = la[i];
                    long l2 = la[i + 1];
                    stream.write(new byte[]{
                            (byte) (l1 >> 4),
                            (byte) (((l1 << 4) & 0b11110000) | ((l2 >> 8) & 0b00001111)),
                            (byte) l2,
                    });
                }
            }
        } else {
            for (long[] la : table) {
                for (long l : la) {
                    if (type == FATType.FAT16) {
                        stream.write(ByteBuffer.allocate(2).putShort((short) l).array());
                    } else {
                        stream.write(ByteBuffer.allocate(4).putInt((int) l).array());
                    }
                }
            }
        }
    }

}
