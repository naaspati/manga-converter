package samrock.converters;

import static sam.console.ansi.ANSI.blue;
import static sam.console.ansi.ANSI.createBanner;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.console.vt100.VT100.erase_end_of_line;
import static sam.console.vt100.VT100.save_cursor;
import static sam.console.vt100.VT100.unsave_cursor;
import static sam.swing.popup.SwingPopupShop.setPopupsRelativeTo;
import static sam.swing.popup.SwingPopupShop.showHidePopup;
import static sam.swing.utils.SwingUtils.copyToClipBoard;
import static sam.swing.utils.SwingUtils.dirPathInputOptionPane;
import static sam.swing.utils.SwingUtils.filePathInputOptionPane;
import static sam.swing.utils.SwingUtils.inputDialog;
import static sam.swing.utils.SwingUtils.showErrorDialog;

import java.awt.Dialog.ModalityType;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.TextField;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import sam.manga.samrock.Chapter;
import sam.manga.samrock.SamrockDB;
import sam.manga.tools.MangaTools;
import sam.myutils.fileutils.FilesUtils;
import sam.myutils.myutils.MyUtils;
import sam.myutils.renamer.RemoveInValidCharFromString;
import sam.properties.myconfig.MyConfig;
import sam.sql.sqlite.SqliteManeger;
import sam.swing.progress.MyProgressMonitor;

public class MangaConvert {
    private static final Path MANGAROCK_DB_BACKUP = Paths.get(MyConfig.MANGAROCK_DB_BACKUP);
    private static final Path MANGAROCK_INPUT_DB = Paths.get(MyConfig.MANGAROCK_INPUT_DB);
    private static final Path MANGAROCK_INPUT_FOLDER = Paths.get(MyConfig.MANGAROCK_INPUT_FOLDER);
    private static final Path MANGA_DATA_FOLDER = Paths.get(MyConfig.MANGA_DATA_FOLDER);
    private static final Path MANGA_FOLDER = Paths.get(MyConfig.MANGA_FOLDER);
    private static final Path SAMROCK_CLOUD_STORAGE = Paths.get(MyConfig.SAMROCK_CLOUD_STORAGE);
    private static final Path SAMROCK_DB = Paths.get(MyConfig.SAMROCK_DB);
    private static final Path SAMROCK_THUMBS_FOLDER = Paths.get(MyConfig.SAMROCK_THUMBS_FOLDER);

    private final Function<Object, String> removeMangaFolderFromString;

    static final File  MANGAROCK_DATABASES_BACKUP = MANGAROCK_DB_BACKUP.toFile();
    static final File  THUMBS_FOLDER = SAMROCK_THUMBS_FOLDER.toFile();
    static final File  DATA_FOLDER = MANGA_DATA_FOLDER.toFile();

    static final Path  MANGALIST_ALPHABATIC = MANGA_DATA_FOLDER.resolve("mangalist_alphabatic.html");
    static final Path  MANGALIST_LASTMODIFIED = MANGALIST_ALPHABATIC.resolveSibling("mangalist_lastmodified.html");

    static final Path BACKUP_FOLDER = Paths.get("backups");
    static final Path  GARBAGE_FOLDER = Paths.get("garbaged");
    static final Path LOGS_FOLDER = Paths.get("logs");


    /**
     * contains new_manga(s) manga_id
     */
    private final HashSet<String> newMangasSet = new HashSet<>();
    private final Path newMangasSetBackup = BACKUP_FOLDER.resolve(RemoveInValidCharFromString.removeInvalidCharsFromFileName("HashSet<String> newMangasSet")+".dat");

    private final HashSet<File> conversionFileSet = new HashSet<>();
    private final Path conversionFileSetBackup = BACKUP_FOLDER.resolve(RemoveInValidCharFromString.removeInvalidCharsFromFileName("HashSet<File> conversionFileSet")+".dat");

    static final int MANGA_ID = 0;
    static final int MANGA_DIR_NAME = 1;

    static final int CHAPTER_FOLDER_NAME = 0;
    static final int CHAPTER_DIR_NAME = 1;

    /**
     * {manga_id, manga_dir_name} -> ArrayList({chapter_folder_name, chapter_dir_name})
     * <br>
     * <b>beware: </b>consider the scenario -> u asked conversionData.get(k2), where k2 is a String[2] such that Arrays.equals(k, k2) = true,  
     * but conversionData.get(k2)  = null, why?  because Objects.equals(k, k2) = false
     */
    private final HashMap<String[], ArrayList<String[]>> conversionData = new HashMap<>();
    private final Path conversionDataBackup = BACKUP_FOLDER.resolve(RemoveInValidCharFromString.removeInvalidCharsFromFileName("HashMap<String[manga_id, manga_dir_name], ArrayList<String[chapter_folder_name, chapter_dir_name]>> conversionData")+".dat");

    private final MyProgressMonitor progressBar;

    public MangaConvert() {

        try {
            Files.createDirectories(BACKUP_FOLDER);
            Files.createDirectories(GARBAGE_FOLDER);
            Files.createDirectories(LOGS_FOLDER);
        } catch (IOException e) {
            showErrorDialog("Error while Directory Creating", e);

            System.exit(0);
        }

        String mangaFolderString = MANGA_FOLDER.toString();
        removeMangaFolderFromString = object -> object == null ? null : object.toString().replace(mangaFolderString, ""); 

        MakeStrip.garbageFolder = GARBAGE_FOLDER;

        progressBar = new MyProgressMonitor("Converter", 0, 100);
        progressBar.setVisible(true);
        progressBar.setExitOnClose(true);

        progressBar.setReset("Waiting");
    }

    public void clean(){
        BACKUP_FOLDER.toFile().delete();
        GARBAGE_FOLDER.toFile().delete();
        LOGS_FOLDER.toFile().delete();
        if(scanner != null)
            scanner.close();
    }

    /**
     * this is to convert folders in {@link #MANGAROCK_INPUT_FOLDER}, and move to {@link #MANGA_FOLDER} and than do the complete {@link #cleanupsAndUpdates()} 
     */
    public void mangaRockMoverConverter(boolean onlyMove){
        if(Files.notExists(MANGAROCK_INPUT_FOLDER)){
            printError("Folder not found: ", ""+MANGAROCK_INPUT_FOLDER);
            copyToClipBoard(MANGAROCK_INPUT_FOLDER.getFileName().toString());

            if(!confirm("try again?"))
                return;

            if(Files.notExists(MANGAROCK_INPUT_FOLDER)){
                printError("Folder not found: ", ""+MANGAROCK_INPUT_FOLDER);
                copyToClipBoard(MANGAROCK_INPUT_FOLDER.getFileName().toString());
                return;
            }
        }
        if(Files.notExists(MANGAROCK_INPUT_DB)){
            printError("Database file not found: ", MANGAROCK_INPUT_DB);
            return;
        }
        if(Files.notExists(SAMROCK_DB)){
            printError("Database file not found: ", SAMROCK_DB);
            return;
        }
        if(MANGAROCK_INPUT_FOLDER.toFile().list().length == 0){
            printError("Empty folder: ", MANGAROCK_INPUT_FOLDER);
            return;
        }
        readDatabase();

        if(conversionData.isEmpty()){
            println(red("conversionData is empty"));
            return;
        }

        progressBar.setReset("Moving Files");
        progressBar.resetProgress();
        progressBar.setMaximum(conversionData.values().stream().mapToInt(ArrayList::size).sum());

        Path inputFolder = MANGAROCK_INPUT_FOLDER;

        conversionData.forEach((mangaData, chapterDataList) -> {
            Path mangaDir, temp = null;
            try {
                mangaDir = temp = MANGA_FOLDER.resolve(mangaData[MANGA_DIR_NAME]);
                if (Files.notExists(temp)) {
                    Files.createDirectories(temp);
                    newMangasSet.add(mangaData[MANGA_ID]);
                }
            } catch (Exception e) {
                printError(e, "Failed to create manga_dir (manga_id, manga_name, manga_dir_path) => (", mangaData[MANGA_ID], mangaData[MANGA_DIR_NAME], removeMangaFolderFromString.apply(temp), ')');
                return;
            }

            chapterDataList.forEach(s -> {
                Path src = null, target = null;
                try {
                    src = inputFolder.resolve(s[CHAPTER_FOLDER_NAME]);
                    target = mangaDir.resolve(s[CHAPTER_DIR_NAME]);

                    if(Files.exists(target)){
                        Path p2 = GARBAGE_FOLDER.resolve(target.getName(target.getNameCount() - 2));

                        if(Files.notExists(p2))
                            Files.createDirectories(p2);

                        Files.move(target, p2.resolve(target.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        printError("garbaged ", removeMangaFolderFromString.apply(target));
                    }
                    Files.move(src, target, StandardCopyOption.REPLACE_EXISTING);
                    conversionFileSet.add(target.toFile());
                }
                catch (IOException|InvalidPathException e) {
                    printError(e, "Error occured with: (manga_id, manga_name, chapter_src, chapter_target) => (", mangaData[MANGA_ID], mangaData[MANGA_DIR_NAME], removeMangaFolderFromString.apply(src), removeMangaFolderFromString.apply(target), ')');
                }
                progressBar.increaseBy1();
            });
        });

        createSavePoint();

        if(onlyMove){
            progressBar.setCompleted();
            println("Only moved, No conversion made");
            return;
        }

        startConversionThread();

        if(cleanupsAndUpdates())
            deleteSavePoint();
        progressBar.setCompleted();
        MyUtils.beep(5);
    }


    Scanner scanner;

    private boolean confirm(String msg) {
        if(scanner == null)
            scanner = new Scanner(System.in);

        return consoleConfirm(scanner, msg);
    }
    public static boolean consoleConfirm(Scanner scanner, String msg) {
        return "y".equalsIgnoreCase(consoleConfirm(msg, scanner, "y", "n"));    
    }

    public static String consoleConfirm(String msg, Scanner scanner, String... options) {
        Objects.requireNonNull(options);

        if(options.length == 0)
            throw new IllegalStateException("options.length = 0");

        System.out.print(yellow((msg == null ? "" : msg )+ " "+Arrays.toString(options)+" "));

        save_cursor();
        while(scanner.hasNextLine()) {
            String s = scanner.nextLine();

            if(Stream.of(options).anyMatch(t -> t.equalsIgnoreCase(s)))
                return s;

            System.out.println("\007");

            unsave_cursor();
            erase_end_of_line();
            save_cursor();
        }

        return null;
    }

    /**
     * this method adds converting folder to thread
     */
    public void startConversionThread() {
        if(conversionFileSet == null || conversionFileSet.isEmpty()){
            printError("nothing to convert");
            progressBar.setFailed();
            return;
        }

        String totalSign = " / ".concat(String.valueOf(conversionFileSet.size()));

        progressBar.setReset("Converting");
        progressBar.resetProgress();
        progressBar.setMaximum(conversionFileSet.size());
        progressBar.setTitle("0 ".concat(totalSign));

        Consumer<String> progresser = name -> {
            progressBar.setString("Converted: ".concat(name));
            progressBar.setTitle(String.valueOf(progressBar.getCurrentProgress()).concat(totalSign));
            progressBar.increaseBy1();
        };

        ExecutorService executor = Executors.newFixedThreadPool(4);

        conversionFileSet.forEach(f -> {
            try {
                executor.execute(new MakeStrip(f, progresser));
            } catch (Exception e) {
                printError(e, "error in thread for folder ", removeMangaFolderFromString.apply(f));
            }
        });

        executor.shutdown();

        while (!executor.isTerminated()) {}

        progressBar.setCompleted();
    }
    /**
     * saves {@link #newMangasSet} as it is (HashSet&#060;String&#062;)<br>
     * converts {@link #conversionFileSet} (which is HashSet&#060;Path&#062;) to Set&#060;File&#062; and saves it (Path is not Serializable)
     */
    public void createSavePoint(){
        try {
            if(newMangasSet != null && !newMangasSet.isEmpty()){
                FilesUtils.writeObjectToFile(newMangasSet, newMangasSetBackup);
                println("backup: ", newMangasSetBackup.getFileName());
            }
            if(conversionFileSet != null && !conversionFileSet.isEmpty()){
                FilesUtils.writeObjectToFile(conversionFileSet, conversionFileSetBackup);
                println("backup: ",conversionFileSetBackup.getFileName());
            }
            if(!conversionData.isEmpty()){
                FilesUtils.writeObjectToFile(conversionData, conversionDataBackup);
                println("backup: ",conversionDataBackup.getFileName());
            }
            println("backups created\n");
        } catch (IOException e2) {
            printError(e2, "Error while creating backups");
        }
    }

    public void deleteSavePoint(){
        try {
            Files.deleteIfExists(newMangasSetBackup);
            Files.deleteIfExists(conversionFileSetBackup);
            Files.deleteIfExists(conversionDataBackup);
            System.out.println(green("backups deleted"));
        } catch (Exception e2) {
            printError(e2, "Error while backups delete");
        }
    }

    /**
     * this method is useful when mangarock converter is intrupped and want to resume later
     */
    public void resumeMangarockConversion(){
        if(Files.notExists(conversionFileSetBackup)){
            printError("Resume is not possible, file not found ", removeMangaFolderFromString.apply(conversionFileSetBackup));
            return;
        }
        try {
            conversionFileSet.addAll(FilesUtils.readObjectFromFile(conversionFileSetBackup));
            conversionFileSet.removeIf(f -> !f.exists());
            if(Files.exists(newMangasSetBackup))
                newMangasSet.addAll(FilesUtils.readObjectFromFile(newMangasSetBackup));
            if(Files.exists(conversionDataBackup))
                conversionData.putAll(FilesUtils.readObjectFromFile(conversionDataBackup));
        } catch (ClassNotFoundException|IOException e) {
            printError(e, "Error while reading data", newMangasSetBackup , conversionFileSetBackup);
            return;
        }

        startConversionThread();
        if(cleanupsAndUpdates())
            deleteSavePoint();

        progressBar.setCompleted();
    }

    private int getChapterCountPc(Path mangaFolder) {
        if(mangaFolder == null || Files.notExists(mangaFolder) || !Files.isDirectory(mangaFolder)){
            printError("Error while counting chapters for", removeMangaFolderFromString.apply(mangaFolder),"File exists: ", (mangaFolder == null)? "null" : Files.exists(mangaFolder), "isDirectory: ", mangaFolder == null ? "null" : Files.isDirectory(mangaFolder));
            return 0;
        }

        String[] names = mangaFolder.toFile().list();

        if(names.length == 1)
            return 1;

        return (int) Stream.of(names).map(s -> s.replaceFirst(" - \\d\\.jpe?g", "")).distinct().count();
    }

    /**
     * {manga_id, manga_dir_name} -> ArrayList({chapter_folder_name, chapter_dir_name})
     * @return
     */
    public void readDatabase() {
        progressBar.setString("Reading Database(s)");

        try (SqliteManeger sqliteManeger = new SqliteManeger(MANGAROCK_INPUT_DB.toString(), true);
                ) {
            do {
                ArrayList<String> fileNames = new ArrayList<>(Arrays.asList(MANGAROCK_INPUT_FOLDER.toFile().list()));

                if(fileNames.isEmpty())
                    break;

                //manga_id -> ArrayList({chapter_folder_name, chapter_dir_name})
                HashMap<String, ArrayList<String[]>> downloadTask = new HashMap<>();

                sqliteManeger.executeQueryAndIterateResultSet("SELECT dir_name, chapter_name, manga_id FROM DownloadTask WHERE dir_name LIKE '%/%'", 
                        rs -> {
                            String chapterFolderName = rs.getString("dir_name");
                            chapterFolderName = chapterFolderName.substring(chapterFolderName.lastIndexOf('/')+1, chapterFolderName.length());
                            String manga_id = rs.getString("manga_id");

                            if(fileNames.contains(chapterFolderName)){
                                String chapterDirName = MangaTools.formatMangaChapterName(rs.getString("chapter_name"));
                                if(chapterDirName == null)
                                    printError("formatChapterName() failed to format: " +rs.getString("chapter_name"));
                                else{
                                    downloadTask.putIfAbsent(manga_id, new ArrayList<>());
                                    downloadTask.get(manga_id).add(new String[]{chapterFolderName, chapterDirName});
                                }
                            }
                        });

                downloadTask.values().removeIf(ArrayList::isEmpty);

                sqliteManeger.executeQueryAndIterateResultSet("SELECT _id, name FROM Manga WHERE _id IN("+String.join(",", downloadTask.keySet())+")", 
                        rs -> {
                            String dir_name = MangaTools.formatMangaDirName(rs.getString("name"));
                            if(dir_name == null)
                                printError("formatDirName() failed to format, ", rs.getString("name"));
                            else    
                                conversionData.put(new String[]{rs.getString("_id"), dir_name}, downloadTask.get(rs.getString("_id")));
                        });
            } while (false);
        }
        catch (SQLException|NullPointerException | InstantiationException | IllegalAccessException | ClassNotFoundException | IOException e) {
            showErrorDialog("Error while Directory Creating", e);
            printError(e, "Error in readDatabase()");
            progressBar.setFailed();
        }
    }

    /**
     * brute check for chapter folder not converted in {@link #MANGA_FOLDER}
     */
    public void checkfoldersNotConverted(){
        HashMap<File, Long> modifiedDates = new HashMap<>();

        Path modifiedDatesPath = Paths.get("modifiedDates.dat");

        try {
            if(Files.exists(modifiedDatesPath))
                modifiedDates.putAll(FilesUtils.readObjectFromFile(modifiedDatesPath));
        } catch (ClassNotFoundException | IOException e) {}

        progressBar.setReset("Checking folders not converted");
        progressBar.setString("Listing manga in: "+MANGA_FOLDER);

        File[] mangaFiles = MANGA_FOLDER.toFile().listFiles();

        progressBar.setMaximum(mangaFiles.length);
        progressBar.resetProgress();
        progressBar.setString("Checking");

        int skipped = 0;
        for (File f : mangaFiles) {
            Long lm = f.lastModified();
            if(Objects.equals(lm, modifiedDates.get(f))){
                skipped++;
                continue;
            }
            modifiedDates.put(f, lm);

            File[] files = null;
            if(f.isDirectory() && !f.equals(DATA_FOLDER) && (files = f.listFiles(File::isDirectory)).length != 0){
                println(yellow(f.getName()));
                conversionFileSet.addAll(Stream.of(files).peek(f2 -> println("\t",f2.getName())).collect(Collectors.toSet()));
                println();
            }
            progressBar.increaseBy1();
        }

        System.out.println(yellow("SKIPPED: ")+skipped);
        try {
            FilesUtils.writeObjectToFile(modifiedDates, modifiedDatesPath);
        } catch (IOException e) {}

        if(!conversionFileSet.isEmpty()){
            createSavePoint();
            if(confirm("Wanna convert"))
                resumeMangarockConversion();
            deleteSavePoint();
        }
        progressBar.setCompleted();
    }

    public final int PROGRESS_STEPS_IN_CLEANUP_UPDATE = 10;

    public void fakeCleanupsANDUpdates() {
        System.out.println(createBanner("Fake Cleanups And Updates"));

        try {
            if(Files.exists(conversionFileSetBackup)){
                conversionFileSet.addAll(FilesUtils.readObjectFromFile(conversionFileSetBackup));
                conversionFileSet.removeIf(p -> !p.exists());
            }

            if(Files.exists(newMangasSetBackup))
                newMangasSet.addAll(FilesUtils.readObjectFromFile(newMangasSetBackup));

        } catch (ClassNotFoundException|IOException e) {
            printError(e, "Error while reading conversionPathList from: ", conversionFileSetBackup);
            return;
        }

        if(confirm("wanna make fake new manga list")){
            File file = filePathInputOptionPane("<html>Enter file path containing manga id(s) <br> each separated by new line</html>");

            if(file != null){
                try {
                    List<String> list = Files.readAllLines(file.toPath());

                    newMangasSet.addAll(list.stream().map(s -> s.replaceAll("\\D+", "").trim()).filter(s -> s.matches("\\d+")).collect(Collectors.toSet()));
                } catch (IOException e) {
                    printError(e, "Error while loading manga id(s) from: ", file);
                }
            }
        }
        if(!newMangasSet.isEmpty()){
            println(green("Id(s) in new manga set: "));
            println("\t", yellow(newMangasSet.stream().collect(Collectors.joining("\r\n\t"))));
            println();
            if(!confirm("wanna proceed?"))
                return;
        }

        cleanupsAndUpdates();

        progressBar.setCompleted();
    }

    public void addNewMangaManually(String id, String url) {
        try (SamrockDB samrock = new SamrockDB();
                SqliteManeger mangarock = new SqliteManeger(MANGAROCK_DATABASES_BACKUP.toString(), true);
                ) {
            ResultSet rs = mangarock.executeQuery("SELECT _id, name, description FROM Manga WHERE _id = ".concat(id));

            if(!rs.next()){
                System.out.println("not data with manga_id in mangarock: "+id);
                showHidePopup("not data with manga_id: "+id, 1500);
                return;
            }

            final String name = rs.getString("name");
            final String description = rs.getString("description");
            rs.close();

            rs = samrock.getDBManeger().executeQuery("SELECT manga_id, manga_name FROM MangaData WHERE manga_id = ".concat(id));

            if(rs.next()){
                System.out.println("samrock has data with this id: "+id);
                System.out.println("id: " +id);
                System.out.println("name: " +rs.getString("name"));
                JOptionPane.showMessageDialog(null, "existing data in samrock: "+id);
                rs.close();
                return;
            }
            rs.close();

            System.out.println("----------------------------\nid: " +id);
            System.out.println("name: " +name);
            System.out.println(yellow("description: \n"+description+"\n\n"));

            if(!confirm("confirmed?")){
                System.out.println(red("cancelled"));
                showHidePopup("cancelled", 1500);
                return;
            }

            if(url != null){
                rs = samrock.getDBManeger().executeQuery("SELECT * FROM MangaUrls WHERE manga_id = "+id);

                if(rs.next()){
                    String url2 = rs.getString("mangafox");
                    String name2 = rs.getString("manga_name");
                    rs.close();

                    if(!name.equals(name2) || !url2.equals(url2)){
                        System.out.println(red("old data"));
                        System.out.println("old:  "+name2+"   "+url2);
                        System.out.println("new:  "+name+"   "+url);

                        if(!confirm("replace?")){
                            System.out.println(red("cancelled"));
                            showHidePopup("cancelled", 1500);
                            return;
                        }
                    }
                    samrock.getDBManeger().executeUpdate("DELETE FROM MangaUrls WHERE manga_id = "+id);
                    samrock.commit();
                }


                samrock.getDBManeger().createPreparedStatementBlock("INSERT INTO MangaUrls VALUES(?,?,?,?)", 
                        ps -> {
                            ps.setString(1, id);
                            ps.setString(2, name);
                            ps.setString(3, url);
                            ps.setString(4, null);
                            ps.addBatch();          
                            ps.executeBatch();
                        });
                samrock.commit();		
            }

            String dir_name = MangaTools.formatMangaDirName(name);

            File file = MANGA_FOLDER.resolve(dir_name).toFile();
            file.mkdirs();

            FilesUtils.openFileNoError(file);

            JOptionPane.showMessageDialog(null, "move files");
        }
        catch (Exception e) {
            showErrorDialog("error while add new manga", e);
            return;
        }

        newMangasSet.add(id);
        cleanupsAndUpdates();
        progressBar.setCompleted();
        System.exit(0);
    }
    public void splitSingleFolder(){
        String folderPath = JOptionPane.showInputDialog(null, "Folder Path", "Double Page Splitter", JOptionPane.QUESTION_MESSAGE);

        if(folderPath == null || folderPath.isEmpty()){
            printError("operation cancelled");
            return;
        }

        if(doublePageSplitter(new File(folderPath)))
            progressBar.setCompleted();
        else
            progressBar.setFailed();

    }

    public void batchFolderSplitting(){
        File folder = dirPathInputOptionPane("Enter path to folder");

        if(folder != null){
            File[] folders = folder.listFiles(File::isDirectory);
            if(folders.length == 0)
                println(red("no folders in folder: "),folder);
            else{
                progressBar.setMaximum(folders.length);
                progressBar.resetProgress();

                boolean success = true;

                for (File f : folders) {
                    progressBar.setString(f.getName());
                    success = success && doublePageSplitter(f);
                    progressBar.increaseBy1();
                }

                if(success)
                    progressBar.setCompleted();
                else
                    progressBar.setFailed();		
            }
        }
    }

    private boolean doublePageSplitter(File folder) {
        println(green("\nWorking on: "),folder);

        if(!folder.exists()){
            println(red("\tFailed: folder does not exists"));
            return false;
        }

        if (!folder.isDirectory()) {
            println(red("\tFailed: not a directory"));
            return false;
        }

        try {
            if(Files.isSameFile(folder.getParentFile().toPath(), MANGA_FOLDER)){
                println(red("\tFailed: Splitting of a MANGA_FOLDER is requested, not in my power , sorry"));
                return false;
            }
        } catch (IOException e1) {
            println(red("\tcheking relation between MANGA_FOLDER and : "+folder.getParentFile()+"  failed"));
            e1.printStackTrace();
            return false;
        }

        if(folder.getParentFile().equals(DATA_FOLDER) || folder.equals(DATA_FOLDER)){
            println(red("\tSplitting of a  DATA_FOLDER/child of DATA_FOLDER is requested, not in my power , bro"));
            return false;
        }

        File[] files = folder.listFiles();

        if (files.length == 0) {
            println(red("\tFailed: empty folder"));
            return false;
        }

        if(Stream.of(files).allMatch(f -> f.getName().matches("\\d+\\.jpe?g"))){
            println(red("\textension found in files , extension will be removed"));
            for (int i = 0; i < files.length; i++) {
                File file2 = new File(files[i].getParent(), files[i].getName().replaceFirst("\\.jpe?g$", "").trim()); 
                files[i].renameTo(file2);
                files[i] = file2; 
            }
        }

        Path path = folder.toPath();

        HashMap<Integer, BufferedImage> images = new HashMap<>();

        boolean errorFound = false;

        for (File f : files) {
            try {
                BufferedImage img = ImageIO.read(f);
                images.put(Integer.parseInt(f.getName()), img);

                //check non images and nullPointers
                int w = img.getWidth();
                int h = img.getHeight();

                //non Image check
                if(w < 100 || h < 100){
                    errorFound = true;
                    println(red("\tImage size Error: ")," size: ",w,"X"+h,"\tfile: ",f.getName());
                }
            } catch (IOException|NullPointerException|NumberFormatException e) {
                errorFound = true;
                println(red("\tImage Check Error: "),e,"\t",f.getName());
            }
        }

        if(errorFound)
            return false;

        boolean error[] = {false};
        images.forEach((i, m) -> {
            try {
                if (m.getWidth() < 1000) {
                    Files.copy(path.resolve(String.valueOf(i)), path.resolve(i+".1"), StandardCopyOption.REPLACE_EXISTING);
                    println("\tpossibly a single page: " , i);
                    return;
                }

                BufferedImage out1 = new BufferedImage(m.getWidth() / 2, m.getHeight(), m.getType());

                out1.createGraphics().drawImage(m, 0, 0, out1.getWidth(), out1.getHeight(), m.getWidth() / 2, 0,
                        m.getWidth(), m.getHeight(), null);

                ImageIO.write(out1, "jpeg", Files.newOutputStream(path.resolve(i+".1")));

                BufferedImage out2 = new BufferedImage(m.getWidth() / 2, m.getHeight(), m.getType());
                out2 = new BufferedImage(m.getWidth() / 2, m.getHeight(), m.getType());

                out2.createGraphics().drawImage(m, 0, 0, out2.getWidth(), out2.getHeight(), 0, 0, m.getWidth() / 2,
                        m.getHeight(), null);

                ImageIO.write(out2, "jpeg", Files.newOutputStream(path.resolve(i+".2")));

                m = null;
                out1 = null;
                out2 = null;

            } catch (IOException e) {
                error[0] = true;
                println("\tError while splitting : ",i,"\t",e);
                return;
            }
        });

        if(error[0])
            return false;

        if(Stream.of(folder.listFiles(f -> f.getName().matches("\\d+"))).allMatch(File::delete)){

            int i[] = {images.keySet().stream().min(Comparator.naturalOrder()).get()};

            if(Stream.of(folder.listFiles())
                    .sorted(Comparator.comparing(f -> Double.parseDouble(f.getName())))
                    .allMatch(f -> f.renameTo(new File(f.getParentFile(), String.valueOf(i[0]++))))){
                println(blue("\tImages Splitted"));
                return true;
            }
            else{
                println(red("\tfailed to rename converted images"));
                return false;
            }
        }
        else{
            printError("\tfailed to remove unconverted images : ", folder);
            return false;
        }
    }

    public void convertFoldersInThisFolder(boolean skipUpdate) {
        String str = inputDialog("paths sepeated by new line");

        if(str == null || str.trim().isEmpty())
            return;

        boolean[] isMangaFolderChild = {false}; 

        Stream.of(str.split("\r?\n"))
        .map(s -> new File(s.replace("\"", "").trim()))
        .filter(File::isDirectory)
        .flatMap(f -> {
            File[] fs = f.listFiles(File::isDirectory);

            try {
                isMangaFolderChild[0] = isMangaFolderChild[0] || Files.isSameFile(f.getParentFile().toPath(), MANGA_FOLDER);
            } catch (IOException e) {
                print("failed to compare files: MANGA_FOLDER and "+f.getParentFile().toPath() );
                e.printStackTrace();
                return Stream.empty();
            } 

            try {
                if(Files.isSameFile(f.toPath(), MANGA_FOLDER)){
                    JOptionPane.showMessageDialog(null, "File is MANGA_FOLDER", "bad folder", JOptionPane.ERROR_MESSAGE);
                    System.exit(0);
                }
            } catch (HeadlessException | IOException e) {
                print("failed to compare files: MANGA_FOLDER and "+f);
                e.printStackTrace();
                return Stream.empty();
            }
            System.out.println(f.getName()+"\t"+yellow(fs.length));
            for (File ff : fs) System.out.println("   "+ff.getName());

            return Stream.of(fs);
        })
        .forEach(conversionFileSet::add);

        if(conversionFileSet.isEmpty()){
            System.out.println(red("not files to convert"));
            return;
        }

        startConversionThread();

        if(isMangaFolderChild[0] && !skipUpdate)
            cleanupsAndUpdates();
        else
            System.out.println("Update Skipped");

        progressBar.setCompleted();
        progressBar.setExitOnClose(true);
        progressBar.dispose();
    }

    /* *****************************************************************
     * ****XXX Below this everything belongs to cleanupsANDUpdates()***** 
     * *****************************************************************
     */
    Function<String, String> coloredMsgMaker = s -> "\u001b[97;104m"+s+"\u001b[0m";
    String coloredStart = "\u001b[94;102m Start \u001b[0m";
    String coloredEnd = "\u001b[94;102m End \u001b[0m\r\n";

    private boolean cleanupsAndUpdates(){
        progressBar.setExitOnClose(false);
        progressBar.setReset("Cleanup and Updates");
        progressBar.setMaximum(PROGRESS_STEPS_IN_CLEANUP_UPDATE);
        progressBar.resetProgress();

        //input clean up
        progressBar.setTitle("input clean up");
        cleanInputs();
        progressBar.increaseBy1();

        //check for folders that are not converted
        progressBar.setTitle("Checking for folder not converted");
        checkFoldersNotConverted();
        progressBar.increaseBy1();

        //write conversion log
        progressBar.setTitle("Writing conversion list");
        writeConversionData();
        progressBar.increaseBy1();

        try (SamrockDB samrock = new SamrockDB();
                SqliteManeger mangarockCon = new SqliteManeger(MANGAROCK_DATABASES_BACKUP.toString(), false);
                ) {

            progressBar.setTitle("proceessing New Mangas");
            processNewMangas(samrock, mangarockCon);
            progressBar.increaseBy1();

            progressBar.setTitle("Processing modified chapters");
            processUpdatedMangas(samrock, mangarockCon);
            progressBar.increaseBy1();

            progressBar.setTitle("performing total Update");
            performTotalUpdate(samrock, mangarockCon);
            progressBar.increaseBy1();

            //report manga not listed in database
            progressBar.setTitle("Report missing mangas in Database");
            remainingChecks(samrock);
            progressBar.increaseBy1();

            //preparing mangalist
            progressBar.setTitle("Preparing manga lists");
            preparingMangalist(samrock);
            progressBar.increaseBy1();

            samrock.commit();
            mangarockCon.commit();

            createPayAttentionFile(samrock);
        }
        catch (Exception e) {
            printError(e, "Cleanups AND updates Error");
            return false;
        }

        try {
            Files.copy(SAMROCK_DB, SAMROCK_CLOUD_STORAGE.resolve(SAMROCK_DB.getFileName().toString().replaceFirst(".db$", "") + "_"+LocalDate.now().getDayOfMonth()+ ".db"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            printError(e, "failed to backup, samrock database");
        }
        progressBar.setCompleted();
        progressBar.setExitOnClose(true);
        return true;
    }

    public void cleanInputs() {
        if(Files.notExists(MANGAROCK_INPUT_DB) && Files.notExists(MANGAROCK_INPUT_FOLDER))
            return;

        println(coloredMsgMaker.apply("starting input cleanups  "),coloredStart);

        if(Files.exists(MANGAROCK_INPUT_FOLDER)){
            if (!MANGAROCK_INPUT_FOLDER.toFile().delete()) {
                for (File f : MANGAROCK_INPUT_FOLDER.toFile().listFiles()) f.delete();
                if(!MANGAROCK_INPUT_FOLDER.toFile().delete())
                    println("Cleanup: com.notabasement.mangarock.android.titan is non empty");
            }
            if(Files.notExists(MANGAROCK_INPUT_FOLDER))
                println("\t delete: ",MANGAROCK_INPUT_FOLDER.getFileName());
        }
        if(Files.exists(MANGAROCK_INPUT_DB)){
            try {
                Files.copy(MANGAROCK_INPUT_DB,
                        MANGAROCK_DATABASES_BACKUP.toPath(), StandardCopyOption.REPLACE_EXISTING);
                println("\t backup: ",MANGAROCK_INPUT_DB.getFileName());
            } catch (IOException e2) {
                printError(e2, "Error while moving moving mangarock.db");
            }
        }

        println(coloredEnd);
    }

    public void checkFoldersNotConverted() {
        if(conversionFileSet != null && !conversionFileSet.isEmpty()){
            String str = conversionFileSet.stream()
                    .filter(File::exists)
                    .map(removeMangaFolderFromString)
                    .collect(Collectors.joining(System.lineSeparator()));

            if(!str.isEmpty()){
                println(coloredMsgMaker.apply("Folders Not converted  ")+coloredStart);
                println(str);
                println(coloredEnd);
            }
        }
    }

    public void writeConversionData() {
        Path p = LOGS_FOLDER.resolve("coversionData.txt");
        try {
            Files.deleteIfExists(p);

            if(conversionData == null || conversionData.isEmpty())
                return;

            StringBuilder b = new StringBuilder();

            if(!newMangasSet.isEmpty()){
                b.append("################ New Manga #############\r\n");
                b.append("Count: ").append(newMangasSet.size()).append("\r\n\r\n");
                conversionData.keySet().stream()
                .filter(s -> newMangasSet.contains(s[MANGA_ID]))
                .forEach(s -> b.append(s[MANGA_ID]).append("\t").append(s[MANGA_DIR_NAME]).append(System.lineSeparator()));
            }

            b.append("\r\n\r\n################ Updated Manga(s) #############\r\n");
            conversionData
            .forEach((s,t) -> {
                if(newMangasSet.contains(s[MANGA_ID]))
                    return;

                b.append(s[MANGA_ID]).append("\t").append(s[MANGA_DIR_NAME]).append("\r\n\t");
                t.forEach(z -> b.append(z[CHAPTER_DIR_NAME]).append("\r\n\t"));
                b.append("\r\n\r\n");
            });

            Files.write(p, b.toString().getBytes(), StandardOpenOption.CREATE,  StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            printError(e, "Error while writing conversion list");
        }
    }

    public void processNewMangas(SamrockDB samrock, SqliteManeger mangarock) throws SQLException, IOException {
        if(newMangasSet.isEmpty())
            return;

        final ArrayList<String> allMangaIds = new ArrayList<>();

        samrock.getDBManeger()
        .executeQueryAndIterateResultSet("SELECT manga_id FROM MangaData", 
                rs -> allMangaIds.add(rs.getString("manga_id")));

        newMangasSet.removeAll(allMangaIds);

        if(newMangasSet.isEmpty())
            return;

        /*
         *1		`manga_id`	INTEGER NOT NULL UNIQUE,
         *2		`dir_name`	VARCHAR NOT NULL UNIQUE,
         *3		`manga_name`	VARCHAR NOT NULL UNIQUE,
         *4		`author`	VARCHAR,
         *5		`chapters`	BLOB NOT NULL,
         *6		`read_count`	SMALLINT NOT NULL DEFAULT 0,
         *7		`unread_count`	SMALLINT NOT NULL DEFAULT 0,
         *8		`description`	TEXT,
         *9		`chap_count_mangarock`	SMALLINT NOT NULL DEFAULT 0,
         *10	`chap_count_pc`	SMALLINT NOT NULL DEFAULT 0,
         *11	`categories`	VARCHAR NOT NULL,
         *12	`isFavorite`	BOOLEAN NOT NULL DEFAULT 0,
         *13	`status`	BOOLEAN NOT NULL DEFAULT 0,
         *14	`rank`	INTEGER NOT NULL,
         *15	`last_read_time`	INTEGER NOT NULL DEFAULT 0,
         *16	`last_update_time`	INTEGER NOT NULL DEFAULT 0,
         *17	`startup_view`	VARCHAR(20) NOT NULL,
         *18	`chapter_ordering`	BOOLEAN NOT NULL,
         *19	`bu_id`	INTEGER NOT NULL UNIQUE,
         */
        StringBuilder b = new StringBuilder("INSERT INTO MangaData VALUES(");
        for (int i = 0; i < 19; i++) b.append('?').append(',');
        b.setCharAt(b.length() - 1, ')');

        String sqlNewManga = b.toString();

        PreparedStatement insertManga = samrock.getDBManeger().prepareStatement(sqlNewManga);
        PreparedStatement insertLastChap = samrock.getDBManeger().prepareStatement("INSERT INTO LastChap VALUES(?,?,?)");

        println(coloredMsgMaker.apply("Processing new Mangas: ")+coloredStart);

        String sql = "SELECT _id, name, author, description, totalChapters, categories, status, rank FROM Manga WHERE _id IN("+String.join(",", newMangasSet)+")";

        String format1 = "  %-10s%s\n";
        print(green(String.format(format1, "manga_id", "manga_name")));
        
        long[] fakeBuid = {System.currentTimeMillis()};
        mangarock.executeQueryAndIterateResultSet(sql, rs -> {
            int manga_id = rs.getInt("_id");
            String manga_name = rs.getString("name"); 
            String dir_name = MangaTools.formatMangaDirName(manga_name);

            printf(format1, manga_id, manga_name);

            Path mangaFolder = MANGA_FOLDER.resolve(dir_name);

            if(Files.notExists(mangaFolder)){
                println(red("manga_folder not found: ")+"manga_id: "+manga_id+", dir_name: "+dir_name+", full_path: "+mangaFolder);
                return;
            }

            String author = rs.getString("author");
            Chapter[] chapters = Chapter.listChaptersOrderedNaturally(mangaFolder.toFile(), null, true);
            String description = rs.getString("description");
            int chap_count_mangarock = rs.getInt("totalChapters");
            int chap_count_pc = getChapterCountPc(mangaFolder);
            String categories = rs.getString("categories");
            int rank = rs.getInt("rank");
            boolean status = rs.getBoolean("status");

            insertLastChap.setInt(1, manga_id);
            insertLastChap.setString(2, manga_name);
            insertLastChap.setString(3, chapters[chapters.length - 1].getName());
            insertLastChap.addBatch();


            /*
             *1     `manga_id`  INTEGER NOT NULL UNIQUE,
             *2     `dir_name`  VARCHAR NOT NULL UNIQUE,
             *3     `manga_name`    VARCHAR NOT NULL UNIQUE,
             *4     `author`    VARCHAR,
             *5     `chapters`  BLOB NOT NULL,
             *6     `read_count`    SMALLINT NOT NULL DEFAULT 0,
             *7     `unread_count`  SMALLINT NOT NULL DEFAULT 0,
             *8     `description`   TEXT,
             *9     `chap_count_mangarock`  SMALLINT NOT NULL DEFAULT 0,
             *10    `chap_count_pc` SMALLINT NOT NULL DEFAULT 0,
             *11    `categories`    VARCHAR NOT NULL,
             *12    `isFavorite`    BOOLEAN NOT NULL DEFAULT 0,
             *13    `status`    BOOLEAN NOT NULL DEFAULT 0,
             *14    `rank`  INTEGER NOT NULL,
             *15    `last_read_time`    INTEGER NOT NULL DEFAULT 0,
             *16    `last_update_time`  INTEGER NOT NULL DEFAULT 0,
             *17    `startup_view`  VARCHAR(20) NOT NULL,
             *18    `chapter_ordering`  BOOLEAN NOT NULL,
             *19    `bu_id` INTEGER NOT NULL UNIQUE,
             */

            insertManga.setInt(1, manga_id); //manga_id
            insertManga.setString(2, dir_name); //dir_name
            insertManga.setString(3, manga_name); //manga_name
            insertManga.setString(4, author); //author(Boolean)chapters[1]
            insertManga.setBytes(5, Chapter.chaptersToBytes(chapters)); //chapters
            insertManga.setInt(6, 0); //read_count
            insertManga.setInt(7, chapters.length); //unread_count
            insertManga.setString(8, description); //description
            insertManga.setInt(9, chap_count_mangarock); //chap_count_mangarock
            insertManga.setInt(10, chap_count_pc); //chap_count_pc
            insertManga.setString(11, categories); //categories
            insertManga.setBoolean(12, false); //isFavorite
            insertManga.setBoolean(13, status); //status
            insertManga.setInt(14, rank); //rank
            insertManga.setLong(15, 0); //last_read_time
            insertManga.setLong(16, mangaFolder.toFile().lastModified()); //last_update_time
            insertManga.setString(17, "DATA_VIEW"); //startup_view
            insertManga.setBoolean(18, true); //chapter_ordering
            insertManga.setString(19, String.valueOf(fakeBuid[0]++)); //bu_id

            insertManga.addBatch();
        });

        println();
        println(yellow("Found: " +newMangasSet.size()+", execute: "+insertManga.executeBatch().length));
        insertLastChap.executeBatch();
        println();

        insertManga.close();
        insertLastChap.close();
        println(coloredEnd);
    }

    public void processUpdatedMangas(SamrockDB samrock, SqliteManeger mangarock) throws SQLException {
        Statement stmnt = samrock.getDBManeger().createStatement();
        ResultSet rs = stmnt.executeQuery("SELECT manga_id, dir_name, last_update_time, chapters, chapter_ordering FROM MangaData"+ ((newMangasSet.isEmpty())? "" : " WHERE NOT manga_id IN("+String.join(",", newMangasSet)+")"));

        boolean execute = false;

        String sqlUpdateChapters = "UPDATE MangaData SET "
                + "chapters = ?, "//1
                + "read_count = ?, "//2
                + "unread_count = ?, "//3
                + "chap_count_pc = ?, "//4
                + "last_update_time = ? "//5
                + " WHERE manga_id = ?";//6

        PreparedStatement delete = samrock.getDBManeger().prepareStatement("DELETE FROM MangaData WHERE manga_id = ?");
        PreparedStatement update = samrock.getDBManeger().prepareStatement(sqlUpdateChapters);
        PreparedStatement updateLastChap = samrock.getDBManeger().prepareStatement("UPDATE LastChap SET last_chap_name = ? WHERE manga_id = ?");

        StringBuilder b = new StringBuilder();  
        int count = 0;

        while(rs.next()){
            int manga_id = rs.getInt("manga_id");
            String dir_name = rs.getString("dir_name");
            long time = rs.getLong("last_update_time");
            File manga_folder  = new File(MANGA_FOLDER.toFile(), dir_name);

            if(manga_folder.exists()){
                try {
                    long time2 = manga_folder.lastModified();
                    if(time != time2){
                        if(!execute)
                            println(coloredMsgMaker.apply("Processing Updated Mangas: ")+coloredStart);

                        execute = true;

                        println(dir_name);
                        boolean ordering = rs.getBoolean("chapter_ordering");
                        Chapter[] chapters = Chapter.listChaptersOrderedNaturally(manga_folder, Chapter.bytesToChapters(rs.getBytes("chapters")), ordering);								
                        int chap_count_pc = getChapterCountPc(manga_folder.toPath());
                        int readCount = (int) Stream.of(chapters).filter(Chapter::isRead).count();

                        update.setBytes(1, Chapter.chaptersToBytes(chapters));
                        update.setInt(2, readCount);
                        update.setInt(3, chapters.length - readCount);
                        update.setInt(4, chap_count_pc);
                        update.setLong(5, time2);
                        update.setInt(6, manga_id);
                        update.addBatch();

                        updateLastChap.setInt(2, manga_id);
                        updateLastChap.setString(1, chapters[ordering ?  chapters.length - 1 : 0].getName());
                        updateLastChap.addBatch();

                        count++;
                    }
                } catch (SQLException e) {
                    printError(e, "Manga Update Error with (manga_id, dir_name) =>(", manga_id, dir_name, ')');
                }
            }
            else{
                delete.setInt(1, manga_id);
                delete.addBatch();
                b.append("id: ").append(manga_id).append(",  dir_name:").append(dir_name).append(System.lineSeparator());
            }
        }

        if(execute)
            println(yellow("found: "+count+",  execute: "+update.executeBatch().length));

        updateLastChap.executeBatch();

        if(b.length() != 0){
            if(!execute)
                println(coloredMsgMaker.apply("Processing Updated Mangas: ")+coloredStart);
            else
                println("\r\n\r\n");

            execute = true;

            println(red("These Manga(s) will be deleted from database"));
            println();
            println(b.toString());

            if(confirm("wish to delete?"))
                delete.executeBatch();
            else
                red("DELETE Cancelled");
        }

        rs.close();
        update.close();
        delete.close();
        stmnt.close();
        updateLastChap.close();

        if(execute)
            println(coloredEnd);
    }

    public void performTotalUpdate(SamrockDB samrock, SqliteManeger mangarockCon) throws SQLException {

        Statement stmnt = samrock.getDBManeger().createStatement();
        ResultSet rs = stmnt.executeQuery("SELECT manga_id FROM MangaData");

        StringBuilder b = new StringBuilder("SELECT _id, author, categories, description, totalChapters, status, rank FROM Manga WHERE _id IN(");

        while(rs.next()) b.append(rs.getString("manga_id")).append(",");
        b.deleteCharAt(b.length() - 1);
        b.append(") AND last_view IS NOT 0");
        rs.close();
        stmnt.close();

        stmnt = mangarockCon.createStatement();
        rs = stmnt.executeQuery(b.toString());

        PreparedStatement authorP = samrock.getDBManeger().prepareStatement("UPDATE MangaData SET author = ? WHERE manga_id =  ?");
        PreparedStatement descriptionP = samrock.getDBManeger().prepareStatement("UPDATE MangaData SET description = ? WHERE manga_id =  ?");
        PreparedStatement chapCountMangarock_statusP = samrock.getDBManeger().prepareStatement("UPDATE MangaData SET chap_count_mangarock = ?, status = ? WHERE manga_id = ?");
        PreparedStatement categoriesP = samrock.getDBManeger().prepareStatement("UPDATE MangaData SET categories = ? WHERE manga_id =  ?");
        PreparedStatement rankP = samrock.getDBManeger().prepareStatement("UPDATE MangaData SET rank = ? WHERE manga_id  = ?");

        while(rs.next()){
            String author = rs.getString("author");
            String description = rs.getString("description");
            int chap_count = rs.getInt("totalChapters");
            int status = rs.getInt("status");
            String categories = rs.getString("categories");
            int rank = rs.getInt("rank");

            int id = rs.getInt("_id");

            if(author != null && !author.trim().isEmpty()){
                authorP.setString(1, author.trim());
                authorP.setInt(2, id);
                authorP.addBatch();
            }

            if(description != null && !description.trim().isEmpty()){
                descriptionP.setString(1, description.trim());
                descriptionP.setInt(2, id);
                descriptionP.addBatch();
            }

            if(chap_count != 0){
                chapCountMangarock_statusP.setInt(1, chap_count);
                chapCountMangarock_statusP.setInt(2, status);
                chapCountMangarock_statusP.setInt(3, id);
                chapCountMangarock_statusP.addBatch();
            }

            if(categories != null && !categories.replaceAll("\\.", "").trim().isEmpty()){
                categoriesP.setString(1, categories.trim());
                categoriesP.setInt(2, id);
                categoriesP.addBatch();
            }

            rankP.setInt(1, rank);
            rankP.setInt(2, id);
            rankP.addBatch();
        }

        rs.close();
        stmnt.close();

        authorP.executeBatch();
        descriptionP.executeBatch();
        chapCountMangarock_statusP.executeBatch();
        categoriesP.executeBatch();
        rankP.executeBatch();

        authorP.close();
        descriptionP.close();
        chapCountMangarock_statusP.close();
        categoriesP.close();
        rankP.close();
    }

    /**
     * list manga_dir(s) exits in manga_folder but not in database<br>
     * check missing thumbs  <br>
     * missing buid
     * @param samrockCon
     * @throws SQLException 
     */
    public void remainingChecks(SamrockDB samrock) throws SQLException {
        final ArrayList<String> dirNames = new ArrayList<>(Arrays.asList(MANGA_FOLDER.toFile().list()));
        dirNames.remove(DATA_FOLDER.getName());
        dirNames.remove("desktop.ini");

        Statement samrockStmnt = samrock.getDBManeger().createStatement();
        ResultSet rs = samrockStmnt.executeQuery("SELECT manga_id, dir_name, manga_name, bu_id FROM MangaData");
        /**
         * manga_id {String} -> dir_name {String} 
         */
        HashMap<String, String> idDirNameMap = new HashMap<>();

        StringBuilder b = new StringBuilder();
        String format = "  %-10s%s\n";

        while(rs.next()) {
            idDirNameMap.put(rs.getString("manga_id"), rs.getString("dir_name"));

            if(rs.getInt("bu_id") > Integer.MAX_VALUE)
                b.append(String.format(format, rs.getString("manga_id"), rs.getString("manga_name")));
        }
        rs.close();
        samrockStmnt.close();

        if(b.length() != 0){
            println(coloredMsgMaker.apply("Missing bu_id(s)"));
            print(yellow(String.format(format, "manga_id", "manga_name")));
            println(b.toString());
            println(coloredEnd);
        }


        dirNames.removeAll(idDirNameMap.values());

        //Mangas missing From Database
        if(!dirNames.isEmpty()){
            println(coloredMsgMaker.apply("\nMangas missing From Database:  ") +coloredStart);
            String str = String.join(System.lineSeparator(), dirNames);
            printError("Manga Missing From Database\r\n", str);
            println(str);
            println(coloredEnd);
        }

        File[] thumbsListed = THUMBS_FOLDER.listFiles();

        //check missing/extra thumbs 
        if(THUMBS_FOLDER.exists()){
            Map<String, File> filesMap = Stream.of(thumbsListed).collect(Collectors.toMap(f -> f.getName().replaceFirst("(?:_\\d+)?\\.jpe?g$", "").trim(), Function.identity()));

            ArrayList<String> temp = new ArrayList<>(idDirNameMap.keySet());

            idDirNameMap.keySet().removeAll(filesMap.keySet());
            filesMap.keySet().removeAll(temp);

            if(!filesMap.isEmpty()){
                List<File> emptyFolders = filesMap.values().stream()
                        .filter(f -> f.isDirectory() && f.list().length == 0 && f.delete())
                        .collect(Collectors.toList());

                filesMap.values().removeAll(emptyFolders);

                if(!filesMap.isEmpty()){
                    println(coloredMsgMaker.apply("Extra Thumbs  ")+coloredStart);
                    println("  "+String.join("\n  ", filesMap.keySet()));
                    println();

                    if(confirm("Delete Extra thumbs")){
                        filesMap.values().forEach(f -> {
                            if(f.isDirectory()){
                                File[] f2 = f.listFiles();
                                for (File f1 : f2) f1.delete();
                            }
                            f.delete();
                        });
                    }
                    else
                        println(red("\nDelete Refused\n  ")+String.join("\n  ", filesMap.keySet()));
                }
                println(coloredEnd);
            }
            if(!idDirNameMap.isEmpty()){
                println(coloredMsgMaker.apply("Missing Thumbs  ")+coloredStart);
                String format1 = "  %-10s%s\n";
                print(green(String.format(format1, "manga_id", "manga_name")));
                idDirNameMap.forEach((s,t) ->printf(format1, s, t));
                println();
                println(coloredEnd);
            }

            //check repreated thumbs
            Map<String, List<String>> map = Stream.of(thumbsListed).map(File::getName).collect(Collectors.groupingBy(s -> s.replaceFirst("(?:_\\d+)?\\.jpe?g$", "").trim()));

            if(map.values().stream().anyMatch(l -> l.size() != 1)){
                println(coloredMsgMaker.apply("Listing Repeated Thumbs ")+coloredStart);
                map.forEach((s,t) -> {
                    if(t.size() == 1)
                        return;
                    println(s+"\t"+t);
                });
                println(coloredEnd);
            }
        }
        else
            println(red("Thumb Folder Not Found"));
    }

    public void preparingMangalist(SamrockDB samrock) throws SQLException {
        Statement samrockStmnt = samrock.getDBManeger().createStatement();
        ResultSet rs = samrockStmnt.executeQuery("SELECT manga_name, chapters, chapter_ordering, chap_count_pc, last_update_time FROM MangaData");

        ArrayList<Object[]> data = new ArrayList<>(1000);

        while(rs.next()){
            Object[] ob = new Object[4];

            ob[0] = rs.getString("manga_name");
            Chapter[] chapters = Chapter.bytesToChapters(rs.getBytes("chapters"));

            ob[1] = chapters.length == 0 ? "" :( rs.getBoolean("chapter_ordering") ? chapters[chapters.length - 1].getName() : chapters[0].getName());

            ob[2] = rs.getString("chap_count_pc");
            ob[3] = rs.getLong("last_update_time");

            data.add(ob);
        }
        rs.close();
        samrockStmnt.close();

        TreeMap<Character, List<Object[]>> mapAlphabetic = new TreeMap<>(data.stream().collect(Collectors.groupingBy(array -> {
            char c1 = Character.toUpperCase(((String)array[0]).charAt(0));

            if(Character.isAlphabetic(c1))
                return c1;
            else
                return '#';
        })));

        TreeMap<LocalDate, List<Object[]>> mapTime = new TreeMap<>(Comparator.reverseOrder());
        ZoneOffset zoneOffset = ZoneOffset.of("+05:30");

        mapTime.putAll(data.stream().collect(Collectors.groupingBy(array -> LocalDateTime.ofInstant(Instant.ofEpochMilli((Long)array[3]), zoneOffset).toLocalDate())));

        StringBuilder outAlphabet = new StringBuilder("<!DOCTYPE html>\r\n<html lang=\"en\">\r\n <head>\r\n <meta charset=\"utf-8\">\r\n\r\n <!-- Always force latest IE rendering engine (even in intranet) & Chrome Frame\r\n Remove this if you use the .htaccess -->\r\n <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge,chrome=1\">\r\n\r\n <title>Book List</title>\r\n <meta name=\"description\" content=\"\">\r\n <meta name=\"author\" content=\"Sameer\">\r\n\r\n <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\r\n\r\n <!-- Replace favicon.ico & apple-touch-icon.png in the root of your domain and delete these references -->\r\n <link rel=\"apple-touch-icon\" href=\"/apple-touch-icon.png\">\r\n </head>\r\n\r\n <style>\r\n body {\r\n margin-left: 3em;\r\n margin-right: 3em;\r\n font-family: Consolas;\r\n}\r\n\r\n #index {\r\n list-style-type: none;\r\n padding-bottom: 100px;\r\n padding-top: 40px;\r\n font-size: 24pt;\r\n }\r\n \r\n ul {\r\n list-style-type: none;\r\n }\r\n \r\n h1 {\r\n background-color: powderblue;\r\n text-indent: 12pt;\r\n }\r\n \r\n #index li a {\r\n float: left;\r\n padding: 2px;\r\n padding-right: 0.8em;\r\n text-decoration: none;\r\n }\r\n \r\n table, tr{\r\n width:100%;\r\n }\r\n \r\n tr:nth-child(odd) {\r\n background-color:#EAEAEA;\r\n align:center;\r\n }\r\n td:nth-child(even), th:nth-child(even){\r\n text-align:center;\r\n width:5%;\r\n }\r\n \r\n th {\r\n color:white;\r\n background-color:black;\r\n }\r\n \r\n td:nth-child(odd), th:nth-child(odd) {\r\n width:46%;\r\n padding-left:20px;\r\n }\r\n \r\n th:nth-child(odd) {\r\n text-align:left;\r\n }\r\n\r\nheader {\r\nfont-size: 10pt;\r\ntext-align: right;\r\ncolor: white;\r\nbackground-color: black;\r\nwidth: 20em;\r\npadding-right: 1em;\r\nfloat: right;\r\n}\r\n </style>\r\n <body>");

        outAlphabet.append("<header>no. of Mangas: ").append(data.size()).append("<br>Created on: ").append(LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))).append("</header>\r\n");
        outAlphabet.append("<ol id=\"index\">\r\n");

        StringBuilder outTime = new StringBuilder(outAlphabet);
        outTime.append("</ol>\r\n");

        mapAlphabetic.keySet().forEach(s-> outAlphabet.append(String.format("<li><a href=\"#index%1$s\">%1$s</a></li>\r\n", s)));

        outAlphabet.append("</ol>\r\n");

        outAlphabet.append("<table>\r\n");
        outAlphabet.append(String.format("<tr>\r\n<th>%s</th>\r\n<th>%s</th>\r\n<th>%s</th></tr>\r\n", "Manga Name", "Chapter Count", "Last Chapter Name"));
        outAlphabet.append("</table>\r\n");

        mapAlphabetic.forEach((s,t)-> {
            outAlphabet.append(String.format("<h1><a id =\"index%1$s\">%1$s</a></h1>\r\n", s));
            outAlphabet.append("<table>\r\n");

            Collections.sort(t, Comparator.comparing((Object[] d) -> (String)d[0]));

            t.forEach(v -> outAlphabet.append(String.format("<tr>\r\n<td>%s</td>\r\n<td>%s</td>\r\n<td>%s</td></tr>\r\n", v[0], v[2], v[1])));
            outAlphabet.append("</table>\r\n");
        });

        outAlphabet.append("</body>\r\n</html>");

        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL);

        mapTime.forEach((s,t)-> {
            outTime.append(String.format("<h1><a id =\"index%1$s\">%1$s</a></h1>\r\n", formatter.format(s)));
            outTime.append("<table>\r\n");

            Collections.sort(t, Comparator.comparing((Object[] l) -> ((Long)l[3])));
            Collections.reverse(t);

            t.forEach(v -> outTime.append(String.format("<tr>\r\n<td>%s</td>\r\n<td>%s</td>\r\n<td>%s</td></tr>\r\n", v[0], v[2], v[1])));
            outTime.append("</table>\r\n");

        });

        outTime.append("</body>\r\n</html>");

        try {
            Files.write(MANGALIST_ALPHABATIC, outAlphabet.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(MANGALIST_LASTMODIFIED, outTime.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Files.copy(MANGALIST_ALPHABATIC, SAMROCK_CLOUD_STORAGE.resolve(MANGALIST_ALPHABATIC.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(MANGALIST_LASTMODIFIED, SAMROCK_CLOUD_STORAGE.resolve(MANGALIST_LASTMODIFIED.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            printError(e, "Error while preparing mangalists");
        }
    }

    public void createPayAttentionFile(SamrockDB samrock) throws SQLException {
        String format = "%-10s%-5s%-5s%-5s%s\r\n";
        StringBuilder b = new StringBuilder("A = manga_id\r\nB = chap_count_pc\r\nC = chap_count_mangarock\r\nD = chap_count_mangarock - chap_count_pc\r\nE = manga_name\r\n\r\n")
                .append(String.format(format, "A", "B","C","D","E"));

        int count[] = {0};

        samrock.getDBManeger()
        .executeQueryAndIterateResultSet("SELECT manga_id, manga_name, chap_count_pc, chap_count_mangarock FROM MangaData WHERE chap_count_pc < chap_count_mangarock ORDER BY last_update_time DESC", 
                rs -> {
                    b.append(String.format(format, rs.getString("manga_id"), rs.getString("chap_count_pc"), rs.getString("chap_count_mangarock"), rs.getInt("chap_count_mangarock") - rs.getInt("chap_count_pc"), rs.getString("manga_name")));
                    count[0]++; 
                });

        b.insert(0, "\r\n\r\n");
        b.insert(0, count[0]);

        try {
            Files.write(DATA_FOLDER.toPath().resolve("uneven chap_count_pc, chap_count_mangarock.txt"), b.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            printError(e, "Error while uneven chap_count_pc, chap_count_mangarock check path:", DATA_FOLDER.toPath().resolve("uneven chap_count_pc, chap_count_mangarock.txt"));
        }    
    }

    /* ****************************************************
     * *********************Print String *****************
     **************************************************** */

    static final StringBuilder logs = new StringBuilder();

    static void println(Object... args) {
        print(args);
        println();
    }

    static void print(Object... str) {
        int s = logs.length();
        for (Object o : str) logs.append(o);
        System.out.print(logs.substring(s));
    }

    static void println(String str) {
        print(str);
        println();
    }

    static void print(String str) {
        logs.append(str);
        System.out.print(str);
    }

    static void printf(String format, Object...args) { print(String.format(format, args)); }

    static void println() {
        logs.append(System.lineSeparator());
        System.out.println();
    }

    /**
     * saves as comma separated msgs, and exception is written after 'Error: '  
     * @param e
     * @param msgs
     */
    static void printError(Exception e, Object... msgs) {
        if(msgs != null)
            print(msgs);

        if(e != null){
            logs.append("\r\nError: \r\n");
            try(StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    ) {
                e.printStackTrace(pw);
                logs.append(sw.toString());
            } catch (Exception e2) {
                logs
                .append("failed to write stacktrace for error: ")
                .append(e)
                .append("\treason: ")
                .append(e2)
                .append(System.lineSeparator());

            }
            e.printStackTrace(System.out);
            System.out.println();
        }
    }
    static void printError(Object... msgs) {printError(null, msgs);}

    public static void createErrorLog() {
        StringBuilder b = new StringBuilder();
        b.append("[").append(LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))).append("]").append("\r\n\r\n");

        boolean write = false;

        //TODO
        if(MakeStrip.errorsCollected.length() != 0){
            write = true;
            b.append(MakeStrip.errorsCollected);
        }
        if(logs.length() != 0){
            write = true;
            b.append(logs);
        }
        b.append("\r\n\r\n");
        if(write){
            Path target = Paths.get("errorLog.log");
            try {
                Path temp = Files.createTempFile("converter-", "");

                try(OutputStream os = Files.newOutputStream(temp)) {
                    os.write(b.toString().getBytes());

                    if(Files.exists(target))
                        Files.copy(target, os);
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println(green(target+" created"));
            } catch (Exception e) {
                System.out.println(green("failed to create")+target);
            }
        }
    }

    public static void changeMangaId() {
        File mangarockFileBackup = MANGAROCK_DB_BACKUP.toFile();

        File samrock = SAMROCK_DB.toFile();
        File mangarock = MANGAROCK_INPUT_DB.toFile();

        mangarock = mangarock.exists() ? mangarock : mangarockFileBackup.exists() ? mangarockFileBackup : null;

        if(!samrock.exists()){
            System.out.println(red(samrock.getName()+"  not found\t")+samrock);
            return;
        }

        if(mangarock == null){
            System.out.println(red("mangarock.db  not found\t"));
            return;
        }
        final String[] ids = getChangeMangaIds();

        if(ids[0] == null || ids[1] == null){
            System.out.println(red("CANCELED"));
            return;
        }


        final String 
        oldId = ids[0],
        newId = ids[1];

        try (SqliteManeger cs = new SqliteManeger(samrock.toString(), false);
                SqliteManeger cm = new SqliteManeger(mangarock.toString(), false);
                Statement ss = cs.createStatement();
                Statement sm = cm.createStatement();
                ) {
            cs.setAutoCommit(false);
            cm.setAutoCommit(false);

            System.out.println();

            ResultSet rs = sm.executeQuery("SELECT author, name FROM Manga WHERE _id = ".concat(newId));

            if(!rs.next()){
                System.out.println(red("no data found associated with new id : ")+yellow(newId));
                return;
            }

            final String 
            newName = rs.getString("name"),
            newAuthor = rs.getString("author"),
            newDirName = MangaTools.formatMangaDirName(newName);

            rs.close();

            PreparedStatement ps = cs.prepareStatement("SELECT manga_id, manga_name, dir_name FROM MangaData WHERE manga_id = ? OR manga_name = ? OR dir_name = ? OR manga_id = ".concat(oldId));
            //manga_id = ? OR manga_name = ? OR dir_name = ?
            ps.setString(1, newId);
            ps.setString(2, newName);
            ps.setString(3, newDirName);

            rs = ps.executeQuery();

            ArrayList<String[]> data = new ArrayList<>();
            while(rs.next()) data.add(new String[]{rs.getString("manga_id"), rs.getString("manga_name"), rs.getString("dir_name")});
            rs.close();
            ps.close();

            if(data.isEmpty() || data.stream().noneMatch(s -> s[0].equals(oldId))){
                System.out.println(red("no data found associated with old id: ")+yellow(oldId));
                return;
            }

            if(data.size() > 1)
                System.out.println(red("data collisions: "));

            System.out.println(yellow("new data ---------------------------------------------"));
            System.out.printf("[%s, %s, %s, %s]\n", newId, newName, newDirName, newAuthor);
            System.out.println(yellow("\nold data ---------------------------------------------"));
            data.forEach(s -> System.out.println(Arrays.toString(s)));
            System.out.println(yellow("\n---------------------------------------------\n"));

            if(data.size() > 1)
                return;

            if(JOptionPane.showConfirmDialog(null, "Confirm to proceed?") != JOptionPane.YES_OPTION){
                System.out.println(red("Cancelled"));
                return;
            }

            if(!oldId.equals(newId))
                ss.execute("DELETE FROM MangaUrls WHERE manga_id = "+newId);

            final String oldDirName = data.get(0)[2]; 
            if(!oldDirName.equals(newDirName)){
                Path target = MANGA_FOLDER.resolve(newDirName);
                try {
                    if(Files.exists(target))
                        System.out.println(red("failed folder renaming ( new_dirname exists: )")+target.toAbsolutePath());
                    else {
                        Files.move(MANGA_FOLDER.resolve(oldDirName), target);
                        System.out.println(yellow("dir renamed"));	
                    }
                } catch (Exception e) {
                    System.out.println(red("failed folder renaming"));
                    e.printStackTrace(System.out);
                }
                System.out.println();
            }

            System.out.println(yellow("db executes: ---------------------------------------------"));

            ps = cs.prepareStatement("UPDATE LastChap SET manga_id = ?, manga_name = ? WHERE manga_id = ?");
            ps.setString(1, newId);
            ps.setString(2, newName);
            ps.setString(3, oldId);
            System.out.println("LastChap: "+ps.executeUpdate());
            ps.close();

            ps = cs.prepareStatement("UPDATE MangaUrls SET manga_id = ?, manga_name = ? WHERE manga_id = ?");
            ps.setString(1, newId);
            ps.setString(2, newName);
            ps.setString(3, oldId);
            System.out.println("MangaUrls: "+ps.executeUpdate());
            ps.close();

            ps = cs.prepareStatement("UPDATE Recents SET manga_id = ? WHERE manga_id = ?");
            ps.setString(1, newId);
            ps.setString(2, oldId);
            System.out.println("Recents: "+ps.executeUpdate());
            ps.close();

            ps = cs.prepareStatement("UPDATE MangaData SET manga_id = ?, manga_name = ?, dir_name = ?, author = ? WHERE manga_id = ?");
            ps.setString(1, newId);
            ps.setString(2, newName);
            ps.setString(3, newDirName);
            ps.setString(4, newAuthor);
            ps.setString(5, oldId);
            System.out.println("MangaData: "+ps.executeUpdate());
            ps.close();

            System.out.println(yellow("\n---------------------------------------------\n"));

            System.out.println();
            File thumbFolder = new File(THUMBS_FOLDER, oldId);

            if(thumbFolder.exists()){
                if(thumbFolder.isDirectory())
                    for (File f : thumbFolder.listFiles())  f.renameTo(new File(f.getParent(), f.getName().replace(oldId, newId)));

                thumbFolder.renameTo(new File(thumbFolder.getParent(), thumbFolder.getName().replace(oldId, newId)));
            }
            else
                new File(THUMBS_FOLDER, oldId+".jpg").renameTo(new File(THUMBS_FOLDER, newId+".jpg"));

            cs.commit();
            System.out.println(green("Database updated"));
        }
        catch (SQLException | InstantiationException | IllegalAccessException | ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }

    static String[] getChangeMangaIds(){
        final String[] returnV = {null, null};

        UIManager.put("Label.font", new Font("Consolas", Font.BOLD, 25));
        UIManager.put("TextField.font", new Font("Consolas", Font.BOLD, 25));

        JDialog d = new JDialog(null, "change id", ModalityType.APPLICATION_MODAL);
        d.setLayout(new GridBagLayout());
        d.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.EAST , GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0);

        d.add(new JLabel("old id"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        TextField oldIdTF = new TextField(20);
        d.add(oldIdTF, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        d.add(new JLabel("new id"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        TextField newIdTF = new TextField(20);
        d.add(newIdTF, gbc);

        JButton ok = new JButton("OK");

        gbc.gridy = 2;
        d.add(javax.swing.Box.createVerticalStrut(10), gbc);

        gbc.gridy = 3;
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;

        d.add(ok, gbc);

        ((JPanel)d.getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        ok.addActionListener(e -> {
            String oldId = oldIdTF.getText().replace("\"", "").trim();
            String newId = newIdTF.getText().replace("\"", "").trim();

            if(!oldId.matches("\\d+") || !newId.matches("\\d+")){
                setPopupsRelativeTo(d);
                showHidePopup("invalid input(s)", 1500);
                return;
            }
            returnV[0] = oldId;
            returnV[1] = newId;

            d.dispose();
        });

        d.pack();
        d.setLocationRelativeTo(null);
        d.setVisible(true);

        return returnV;
    }

}


