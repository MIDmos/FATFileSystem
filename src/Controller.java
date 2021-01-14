import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class Controller {
    private Scanner scanner;
    private FAT fat = new FAT();

    public void start(Scanner scanner) {
        this.scanner = scanner;
        System.out.println("Welcome to FAT system!");
        System.out.println("Write help to get command list.");
        boolean isEnded = false;
        while (!isEnded) {
            String currentInput = scanner.nextLine().trim();
            if (currentInput.equals("exit")) {
                isEnded = true;
            } else if (currentInput.equals("help") || currentInput.equals("?")) {
                printHelp();
            } else if (currentInput.equals("close")) {
                printResult(fat.close());
            } else if (currentInput.equals("status") || currentInput.equals("info") || currentInput.equals("i")) {
                if (!fat.isDiskOpen()) {
                    System.out.println("You should open a disk to get status");
                } else {
                    DiskSpaceInfo info = fat.getDiskSpaceInfo();
                    if (info != null) {
                        System.out.println(info);
                    } else {
                        System.out.println("Status is not available");
                    }
                }
            } else if (currentInput.startsWith("rm ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 2) {
                    System.out.println("Wrong arguments");
                } else {
                    String path = convertDiskPathToAbsolute(args[1]);
                    printResult(fat.deleteFile(path));
                }
            } else if (currentInput.startsWith("cat ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 2) {
                    System.out.println("Wrong arguments");
                } else {
                    String path = convertDiskPathToAbsolute(args[1]);
                    printResult(fat.cat(path));
                }
            } else if (currentInput.startsWith("cd ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 2) {
                    System.out.println("Wrong arguments");
                } else {
                    String path = convertDiskPathToAbsolute(args[1]);
                    printResult(fat.goToDir(path));
                }
            } else if (currentInput.equals("ls")) {
                FATDirectory dir = fat.listDir(fat.getCurrentPath());
                if (dir == null) {
                    System.out.println("Data not available");
                } else {
                    System.out.println(dir);
                }
            } else if (currentInput.startsWith("ls ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 2) {
                    System.out.println("Wrong arguments");
                } else {
                    String path = convertDiskPathToAbsolute(args[1]);
                    FATDirectory dir = fat.listDir(path);
                    if (dir == null) {
                        System.out.println("Data not available");
                    } else {
                        System.out.println(dir);
                    }
                }
            } else if (currentInput.startsWith("mkdir ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 2) {
                    System.out.println("Wrong arguments");
                } else {
                    String path = convertDiskPathToAbsolute(args[1]);
                    printResult(fat.mkDir(path));
                }
            } else if (currentInput.startsWith("copy-out ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 3) {
                    System.out.println("Wrong arguments");
                } else {
                    String diskPath = convertDiskPathToAbsolute(args[1]);
                    String sysPath = args[2];
                    printResult(fat.copyFileToSystem(diskPath, sysPath));
                }
            } else if (currentInput.startsWith("copy-in ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 3) {
                    System.out.println("Wrong arguments");
                } else {
                    String sysPath = args[1];
                    String diskPath = convertDiskPathToAbsolute(args[2]);
                    printResult(fat.copyFileFromSystem(sysPath, diskPath));
                }
            } else if (currentInput.startsWith("open ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 2) {
                    System.out.println("Wrong arguments");
                } else {
                    String path = args[1];
                    printResult(fat.openDisk(path));
                }
            } else if (currentInput.startsWith("create ")) {
                String[] args = currentInput.split(" ");
                if (args.length != 2) {
                    System.out.println("Wrong arguments");
                } else {
                    String path = args[1];
                    printResult(fat.createDisk(path, chooseParams()));
                }
            } else {
                if (!currentInput.isEmpty())
                    System.out.println("Unknown command");
            }

        }
    }

    private void printResult(OperationResult op) {
        if (op.message != null) {
            System.out.println(op.message);
        } else {
            if (op.isOk) {
                System.out.println("ok");
            }
        }

        if (!op.isOk) {
            if (op.error != null) {
                System.out.println("Do you want a stacktrace? y/n");
                if (scanner.nextLine().trim().toLowerCase().equals("y")) {
                    op.error.printStackTrace();
                }
            }
        }
    }

    private String convertDiskPathToAbsolute(String diskPath) {
        if (diskPath.startsWith("/")) {
            return diskPath;
        } else {
            String[] newPath = Arrays.stream(diskPath.split("/")).filter(v -> !v.trim().isEmpty()).toArray(String[]::new);
            String[] currentPath = Arrays.stream(fat.getCurrentPath().split("/")).filter(v -> !v.trim().isEmpty()).toArray(String[]::new);
            LinkedList<String> pathBuilder = new LinkedList<>(Arrays.asList(currentPath));
            for (String s : newPath) {
                String pathPart = s.trim();
                if (!pathPart.equals(".")) {
                    if (pathPart.equals("..") && pathBuilder.size() > 0) {
                        pathBuilder.removeLast();
                    } else {
                        pathBuilder.add(pathPart);
                    }
                }
            }
            StringBuilder sb = new StringBuilder("/");
            for (String s : pathBuilder) {
                sb.append(s);
                sb.append("/");
            }
            if (pathBuilder.size() > 0)
                sb.setLength(sb.length() - 1);
            return sb.toString();
        }
    }

    private DiskCreationParams chooseParams() {
        System.out.println("Choose disk params:\n1: small (FAT-12) 1Mib\n2: medium (FAT-16) 64Mib\n3: big (FAT-32) 0.5 Gib\n4: custom");
        int option = 0;
        option = chooseOption(4);
        switch (option) {
            case 1:
                return DiskCreationParams.small();
            case 2:
                return DiskCreationParams.medium();
            case 3:
                return DiskCreationParams.big();
        }

        System.out.println("Choose FAT type:\n1: FAT-12\n2: FAT-16\n3: FAT-32");
        option = chooseOption(3);

        FATType fatType = option == 1 ? FATType.FAT12 : option == 2 ? FATType.FAT16 : FATType.FAT32;

        int sectorsInCluster;
        if (fatType == FATType.FAT12) {
            int[] answers = new int[]{1, 2};
            sectorsInCluster = askUserAndChooseOption("Sectors in cluster", answers);
        } else if (fatType == FATType.FAT16) {
            int[] answers = new int[]{4, 8, 16, 32, 64};
            sectorsInCluster = askUserAndChooseOption("Sectors in cluster", answers);
        } else {
            int[] answers = new int[]{1, 8, 16, 32, 64};
            sectorsInCluster = askUserAndChooseOption("Sectors in cluster", answers);
        }

        int bytesInSector;
        int[] answers2 = new int[]{512, 1024};
        bytesInSector = askUserAndChooseOption("Bytes in sector", answers2);

        int sectorsOnDisk;
        if (fatType == FATType.FAT12) {
            int minSectors = 16 * sectorsInCluster;
            int maxSectors = 0xFF6 * sectorsInCluster;
            sectorsOnDisk = askUserAndChooseOption("Sectors on disk", generateSectorsOnDiskAnswers(minSectors, maxSectors));
        } else if (fatType == FATType.FAT16) {
            int minSectors = 0xFF7 * sectorsInCluster;
            int maxSectors = 0xFFF6 * sectorsInCluster;
            sectorsOnDisk = askUserAndChooseOption("Sectors on disk", generateSectorsOnDiskAnswers(minSectors, maxSectors));
        } else {
            int minSectors = 0xFFF7 * sectorsInCluster;
            int maxSectors = 0xFFFFF6 * sectorsInCluster;
            sectorsOnDisk = askUserAndChooseOption("Sectors on disk", generateSectorsOnDiskAnswers(minSectors, maxSectors));
        }
        return DiskCreationParams.custom(bytesInSector, sectorsInCluster, Integer.toUnsignedLong(sectorsOnDisk));
    }

    private void printHelp() {
        System.out.println("List of supported commands");
        System.out.println("create <path> - create new disk and open it");
        System.out.println("open <path> - open existing disk from system");
        System.out.println("copy-in <sys-path> <disk-path> - copy file from system to disk");
        System.out.println("copy-out <disk-path> <sys-path> - copy file from disk to system");
        System.out.println("mkdir <disk-path> - create new directory");
        System.out.println("ls <disk-path> - list directory");
        System.out.println("cd <disk-path> - go to directory at path");
        System.out.println("rm <disk-path> - remove file or directory");
        System.out.println("cat <disk-path> - read file");
        System.out.println("status - print disk space status");
        System.out.println("info - print disk space status");
        System.out.println("close - close current disk");
        System.out.println("help - show help");
        System.out.println("exit - stop program");
    }

    private int chooseOption(int count) {
        int option = 0;
        while (option == 0) {
            try {
                int result = Integer.parseInt(scanner.nextLine().trim());
                if (result >= 1 && result <= count) {
                    option = result;
                } else {
                    System.out.println("Wrong number. Please, choose one from the listed options.");
                }
            } catch (Throwable e) {
                System.out.println("Expected an integer value");
            }
        }
        return option;
    }

    private int askUserAndChooseOption(String paramName, int[] answers) {
        StringBuilder sb = new StringBuilder(paramName);
        sb.append(":");
        for (int i = 1; i <= answers.length; i++) {
            sb.append("\n").append(i).append(": ").append(answers[i - 1]);
        }
        System.out.println(sb.toString());
        int option = 0;
        while (option == 0) {
            try {
                int result = Integer.parseInt(scanner.nextLine().trim());
                if (result >= 1 && result <= answers.length) {
                    option = result;
                } else {
                    System.out.println("Wrong number. Please, choose one from the listed options.");
                }
            } catch (Throwable e) {
                System.out.println("Expected an integer value");
            }
        }
        return answers[option - 1];
    }

    private int[] generateSectorsOnDiskAnswers(int minSectors, int maxSectors) {
        System.out.println("Min = "+minSectors);
        System.out.println("Max = "+maxSectors);
        List<Integer> options = new LinkedList<>();
        long sectorsCount = minSectors;
        while (sectorsCount <= maxSectors) {
            options.add((int) sectorsCount);
            sectorsCount *= 2;
        }
        int[] answers = new int[options.size()];
        for (int i = 0; i < options.size(); i++) {
            answers[i] = options.get(i);
        }
        return answers;
    }
}
