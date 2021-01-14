

import com.sun.istack.internal.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class FAT implements FATFileSystem {
    private String currentPath = "/";
    private BootRecord bootRecord;
    private FileAllocationTable fat;
    private File diskFile;
    private boolean isDiskOpen = false;

    @Override
    public OperationResult close() {
        isDiskOpen = false;
        currentPath = "/";
        bootRecord = null;
        fat = null;
        diskFile = null;
        return OperationResult.ok();
    }

    @Override
    public OperationResult createDisk(String systemPath, DiskCreationParams diskParams) {
        try {
            File systemFile = new File(systemPath);
            if (systemFile.exists()) {
                throw new IllegalArgumentException("File at path " + systemPath + " already exists");
            }
            initWithParams(new BootRecord(diskParams), systemFile);
        } catch (Throwable e) {
            return OperationResult.error("Cannot create disk at path " + systemPath, e);
        }
        return OperationResult.ok("Creation succeed");
    }

    @Override
    public OperationResult openDisk(String systemPath) {
        try {
            File systemFile = new File(systemPath);
            if (!systemFile.exists()) {
                throw new IllegalArgumentException("File at path " + systemPath + " does not exist");
            }
            if (systemFile.isDirectory()) {
                throw new IllegalArgumentException("File at path " + systemPath + " is a directory");
            }
            bootRecord = readBootRecord(systemFile);
            initWithParams(bootRecord, systemFile);
        } catch (Throwable e) {
            return OperationResult.error("Cannot open disk at path " + systemPath, e);
        }
        return OperationResult.ok("Open succeed");
    }

    @Override
    public OperationResult copyFileFromSystem(String systemPath, String diskPath) {
        try {
            File systemFile = new File(systemPath);
            if (!systemFile.exists()) {
                throw new IllegalArgumentException("File at path " + systemPath + " does not exists");
            }
            if (systemFile.isDirectory()) {
                throw new IllegalArgumentException("File at path " + systemPath + " is a directory");
            }
            DirectoryEntry file = getFileOrNull(diskPath);
            if (file != null) {
                throw new IllegalArgumentException("File at path " + systemPath + " already exists");
            }
            String[] split = Arrays.stream(diskPath.split("/")).filter(v -> !v.trim().isEmpty()).toArray(String[]::new);
            String[] fileName = split[split.length - 1].split("\\.");
            String name = fileName[0];
            String ext = fileName.length > 1 ? fileName[1] : "";
            byte[] bytes = Files.readAllBytes(systemFile.toPath());
            FATDirectory parentDir = getParentDirOrNull(diskPath);
            if (parentDir == null) {
                throw new IllegalArgumentException("File at path " + diskPath + " does not exists");
            }
            long start = writeBytesToDisk(bytes);
            parentDir.appendEntry(DirectoryEntry.file(
                    name,
                    ext,
                    start,
                    bytes.length
            ));
            updateFileAtDisk(parentDir.toByteArray(), parentDir.getSelfDirCluster());
        } catch (Throwable e) {
            return OperationResult.error("Copy failed " + systemPath, e);
        }
        return OperationResult.ok("Copy succeed");
    }

    @Override
    public OperationResult copyFileToSystem(String diskPath, String systemPath) {
        try {
            File systemFile = new File(systemPath);
            if (systemFile.exists()) {
                throw new IllegalArgumentException("File at path " + systemPath + " exists. Override option is not supported yet");
            }
            if (systemFile.isDirectory()) {
                throw new IllegalArgumentException("File at path " + systemPath + " is a directory");
            }
            DirectoryEntry file = getFileOrNull(diskPath);
            if (file == null) {
                throw new IllegalArgumentException("File at path " + diskPath + " does not exist");
            }
            if (file.isDir()) {
                throw new IllegalArgumentException("File at path " + diskPath + " is a directory");
            }

            try (FileOutputStream fos = new FileOutputStream(systemFile);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                readFileFromDisk(baos, file);
                baos.writeTo(fos);
            } catch (IOException ioe) {
                throw new IllegalStateException("Cannot write to file", ioe);
            }

        } catch (Throwable e) {
            return OperationResult.error("Copy failed", e);
        }
        return OperationResult.ok("Copy succeed");
    }

    @Override
    public FATDirectory listDir(String diskPath) {
        try {
            if (diskPath.equals("/")) {
                return getRootDir();
            }
            DirectoryEntry file = getFileOrNull(diskPath);
            if (file != null && file.isDir()) {
                return readDirectory(file);
            }
        } catch (Throwable e) {
            return null;
        }
        return null;
    }

    public boolean isDiskOpen() {
        return isDiskOpen;
    }

    @Override
    public DiskSpaceInfo getDiskSpaceInfo() {
        if (bootRecord == null) {
            return null;
        }
        long allBytes = bootRecord.sectorsOnDisk * bootRecord.bytesInSector;
        long freeBytes = fat.getAvailableClustersCount() * bootRecord.sectorsInCluster * bootRecord.bytesInSector;
        return new DiskSpaceInfo(
                bootRecord.getSystemType().name(),
                allBytes,
                freeBytes,
                allBytes - freeBytes
        );
    }

    @Override
    public OperationResult deleteFile(String diskPath) {
        try {
            DirectoryEntry file = getFileOrNull(diskPath);
            if (file == null) {
                return OperationResult.ok("There was no file at path " + diskPath);
            }
            delete(diskPath, file);
        } catch (Throwable e) {
            return OperationResult.error("Delete failed", e);
        }
        return OperationResult.ok("Delete succeed");
    }

    @Override
    public OperationResult mkDir(String diskPath) {
        try {
            DirectoryEntry file = getFileOrNull(diskPath);
            if (file != null) {
                if (file.isDir()) {
                    return OperationResult.ok("Directory was already created");
                } else {
                    throw new IllegalArgumentException("There is a file at path " + diskPath);
                }
            }
            FATDirectory parentDir = getParentDirOrNull(diskPath);
            if (parentDir == null) {
                throw new IllegalArgumentException("Cannot find parent for " + diskPath);
            }
            FATDirectory newDir = FATDirectory.empty(parentDir.getSelfDirCluster());
            long start = writeBytesToDisk(newDir.toByteArray());
            String[] split = Arrays.stream(diskPath.split("/")).filter(v -> !v.trim().isEmpty()).toArray(String[]::new);
            String name = split[split.length - 1].trim();
            if (name.isEmpty() || name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException("Directory cannot have a name \"" + name + "\"");
            }
            parentDir.appendEntry(DirectoryEntry.directory(name, start));
            updateFileAtDisk(parentDir.toByteArray(), parentDir.getSelfDirCluster());
            newDir.setSelfCluster(start);
            updateFileAtDisk(newDir.toByteArray(), start);
        } catch (Throwable e) {
            return OperationResult.error("Make dir failed", e);
        }
        return OperationResult.ok();
    }

    @Override
    public OperationResult goToDir(String diskPath) {
        try {
            DirectoryEntry file = getFileOrNull(diskPath);
            if (file == null) {
                throw new IllegalArgumentException("File at path " + diskPath + " does not exist");
            }
            if (!file.isDir()) {
                throw new IllegalArgumentException("File at path " + diskPath + " is not a directory");
            }
        } catch (Throwable e) {
            return OperationResult.error("Dir change failed", e);
        }
        currentPath = diskPath;
        return OperationResult.ok();
    }

    @Override
    public OperationResult cat(String diskPath) {
        try {
            DirectoryEntry file = getFileOrNull(diskPath);
            if (file == null) {
                throw new IllegalArgumentException("File at path " + diskPath + " does not exist");
            }
            if (file.isDir()) {
                throw new IllegalArgumentException("File at path " + diskPath + " is a directory");
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                readFileFromDisk(baos, file);
                return OperationResult.ok(new String(baos.toByteArray(), StandardCharsets.US_ASCII));
            } catch (IOException ioe) {
                throw new IllegalStateException("Cannot write to file", ioe);
            }

        } catch (Throwable e) {
            return OperationResult.error("Cat failed", e);
        }
    }

    private void delete(String diskPath, DirectoryEntry file) {
        if (getRootDir().getSelfDirEntry().initialCluster == file.initialCluster) {
            throw new IllegalArgumentException("Cannot delete root directory");
        }
        FATDirectory parentDir = getParentDirOrNull(diskPath);
        if (parentDir == null) {
            throw new IllegalStateException("Cannot find file at path " + diskPath);
        }
        parentDir.markForDeletion(file);
        if (!file.isDir()) {
            fat.deleteClustersFrom(file.initialCluster);
        } else {
            deleteRecursively(file);
        }
        updateFileAtDisk(parentDir.toByteArray(), parentDir.getSelfDirCluster());
    }

    private void deleteRecursively(DirectoryEntry file) {
        if (!file.isDir()) {
            fat.deleteClustersFrom(file.initialCluster);
            return;
        }
        FATDirectory dirToDelete = readDirectory(file);
        for (DirectoryEntry e : dirToDelete.entries) {
            if (!e.isSelfDir() && !e.isParentDir())
                deleteRecursively(e);
        }
        fat.deleteClustersFrom(dirToDelete.getSelfDirCluster());
    }

    private DirectoryEntry getFileOrNull(@NotNull String diskPath) {
        if (diskPath.equals("/")) {
            return getRootDir().getSelfDirEntry();
        }
        String[] split = Arrays.stream(diskPath.split("/")).filter(v -> !v.trim().isEmpty()).toArray(String[]::new);
        FATDirectory current = getRootDir();
        int index = 0;
        boolean hasEntry = true;
        while (current != null && index < split.length && hasEntry) {
            hasEntry = false;
            for (DirectoryEntry e : current.entries) {
                if (e.fileNameWithExtension().equals(split[index])) {
                    hasEntry = true;
                    index = index + 1;
                    if (e.isDir()) {
                        current = readDirectory(e);
                    } else {
                        current = null;
                    }
                    if (index == split.length) {
                        return e;
                    }
                }
            }
        }
        return null;
    }

    private FATDirectory getParentDirOrNull(@NotNull String diskPath) {
        String[] split = Arrays.stream(diskPath.split("/")).filter(v -> !v.trim().isEmpty()).toArray(String[]::new);
        if (diskPath.equals("/") || split.length == 1) {
            return getRootDir();
        }
        FATDirectory current = getRootDir();
        int index = 0;
        boolean hasEntry = true;
        while (index < split.length - 1 && hasEntry) {
            hasEntry = false;
            for (DirectoryEntry e : current.entries) {
                if (e.isDir() && e.fileNameWithExtension().equals(split[index])) {
                    hasEntry = true;
                    index = index + 1;
                    current = readDirectory(e);
                    if (index == split.length - 1) {
                        return current;
                    }
                }
            }
        }
        return null;
    }

    private void readFileFromDisk(@NotNull ByteArrayOutputStream stream, @NotNull DirectoryEntry file) throws IOException {
        if (file.isDir()) {
            stream.write(readDirectory(file).toByteArray());
        } else {
            readFileBytesFromCluster(stream, file.initialCluster, file.size);
        }
    }

    private void readFileBytesFromCluster(@NotNull ByteArrayOutputStream stream, @NotNull long startingCluster, @NotNull long size) throws IOException {
        long[] clusters = fat.getFileSequenceWithStartingCluster(startingCluster);
        long readCount = size / bootRecord.bytesInCluster();
        int last = (int) (size % bootRecord.bytesInCluster());
        if (last > 0) readCount++;
        if (readCount > clusters.length) {
            throw new IllegalStateException("Clusters are corrupted");
        }
        for (int i = 0; i < readCount; i++) {
            if (i != readCount - 1 || last == 0) {
                stream.write(readAllBytesInCluster(clusters[i]));
            } else {
                stream.write(readAllBytesInCluster(clusters[i]), 0, last);
            }
        }
    }

    private long writeBytesToDisk(@NotNull byte[] bytes) {
        int bytesInCluster = bootRecord.bytesInCluster();
        int clustersCount = bytes.length / bytesInCluster;
        int last = bytes.length % bytesInCluster;
        if (last > 0) clustersCount++;
        if (!fat.canPutClusters(clustersCount)) {
            throw new IllegalStateException("Space limit was reached");
        }
        long[] clusters = fat.putClusters(clustersCount);
        fillClusters(bytes, bytesInCluster, clusters);
        return clusters[0];
    }

    private void updateFileAtDisk(@NotNull byte[] bytes, @NotNull long initialCluster) {
        int bytesInCluster = bootRecord.bytesInCluster();
        int clustersCount = bytes.length / bytesInCluster;
        int last = bytes.length % bytesInCluster;
        if (last > 0) clustersCount++;

        long[] clusters = fat.updateClusterSequence(clustersCount, initialCluster);
        fillClusters(bytes, bytesInCluster, clusters);
        writeDiskToFile(diskFile);
    }

    private void fillClusters(@NotNull byte[] bytes, int bytesInCluster, long[] clusters) {
        for (int i = 0; i < clusters.length; i++) {
            if (i != clusters.length - 1) {
                writeBytesToCluster(clusters[i], Arrays.copyOfRange(bytes, i * bytesInCluster, (i + 1) * bytesInCluster));
            } else {
                writeBytesToCluster(clusters[i], Arrays.copyOfRange(bytes, i * bytesInCluster, bytes.length));
            }
        }
    }

    private FATDirectory readDirectory(@NotNull DirectoryEntry directory) {
        if (!directory.isDir()) {
            throw new IllegalArgumentException("Cannot read directory at " + directory.initialCluster);
        }
        long[] clusters = fat.getFileSequenceWithStartingCluster(directory.initialCluster);
        ByteBuffer buffer = ByteBuffer.allocate(clusters.length * bootRecord.bytesInCluster());
        for (long c : clusters) {
            buffer.put(readAllBytesInCluster(c));
        }
        return FATDirectory.read(buffer.array());
    }

    private BootRecord readBootRecord(@NotNull File systemFile) {
        try (FileInputStream fis = new FileInputStream(systemFile)) {
            return BootRecord.read(fis);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read boot record", e);
        }
    }

    private void initWithParams(@NotNull BootRecord bootRecord, @NotNull File diskFile) throws IOException {
        this.bootRecord = bootRecord;
        this.diskFile = diskFile;

        if (!diskFile.exists()) {
            long initialCLuster = bootRecord.getRootCluster();
            fat = FileAllocationTable.createTableWithSize(bootRecord.getSystemType(), bootRecord.clustersOnDisk(), initialCLuster);
            FATDirectory root = FATDirectory.empty(initialCLuster);
            updateFileAtDisk(root.toByteArray(), initialCLuster);
            writeDiskToFile(diskFile);
        } else {
            try (InputStream fatStream = bootRecord.getFatSection(diskFile)) {
                fat = FileAllocationTable.read(bootRecord.getSystemType(), fatStream, bootRecord.clustersOnDisk());
            }
        }
        isDiskOpen = true;
    }

    private FATDirectory getRootDir() {
        long[] clusters = fat.getFileSequenceWithStartingCluster(bootRecord.getRootCluster());
        ByteBuffer buffer = ByteBuffer.allocate(clusters.length * bootRecord.bytesInCluster());
        for (long c : clusters) {
            buffer.put(readAllBytesInCluster(c));
        }
        return FATDirectory.read(buffer.array());
    }

    private void writeBytesToCluster(long cluster, byte[] bytes) {
        try (RandomAccessFile raf = new RandomAccessFile(diskFile, "rw")) {
            raf.seek(bootRecord.firstSectorOffset() + cluster * bootRecord.bytesInCluster());
            if (bytes.length == bootRecord.bytesInCluster()) {
                raf.write(bytes);
            } else {
                byte[] fullBytes = new byte[bootRecord.bytesInCluster()];
                System.arraycopy(bytes, 0, fullBytes, 0, bytes.length);
                raf.write(fullBytes);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write to cluster", e);
        }
    }

    private void writeDiskToFile(@NotNull File systemFile) {
        try (RandomAccessFile raf = new RandomAccessFile(systemFile, "rw")) {
            raf.write(bootRecord.toByteArray());
            fat.write(bootRecord.getSystemType(), raf);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write to cluster", e);
        }
    }

    private byte[] readAllBytesInCluster(long cluster) {
        try (RandomAccessFile raf = new RandomAccessFile(diskFile, "r")) {
            raf.seek(bootRecord.firstSectorOffset() + cluster * bootRecord.bytesInCluster());
            byte[] bytes = new byte[bootRecord.bytesInCluster()];
            raf.read(bytes);
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write to cluster", e);
        }
    }

    public String getCurrentPath() {
        return currentPath;
    }
}
