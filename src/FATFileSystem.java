import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

public interface FATFileSystem {
    OperationResult createDisk(@Nullable String systemPath, @Nullable DiskCreationParams diskParams);

    OperationResult openDisk(@NotNull String systemPath);

    OperationResult copyFileFromSystem(@NotNull String systemPath, @NotNull String diskPath);

    OperationResult copyFileToSystem(@NotNull String diskPath, @NotNull String systemPath);

    OperationResult mkDir(@NotNull String diskPath);

    FATDirectory listDir(@NotNull String diskPath);

    OperationResult goToDir(@NotNull String diskPath);

    OperationResult deleteFile(@NotNull String diskPath);

    OperationResult cat(@NotNull String diskPath);

    OperationResult close();

    DiskSpaceInfo getDiskSpaceInfo();
}
