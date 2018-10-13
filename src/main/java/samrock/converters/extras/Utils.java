package samrock.converters.extras;

import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.myutils.MyUtilsSystem.lookup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import sam.config.MyConfig;
import sam.fileutils.FilesWalker;
import sam.fileutils.FilesWalker.FileWalkResult;

public class Utils {
    private Utils() {}
    
    public static final Path APP_DATA = Paths.get(Optional.of(lookup("APP_DATA")).orElse("app_data")) ;
    public static final Path CACHE_DIR = APP_DATA.resolve("cache");
    
    public static final Path MANGAROCK_DB_BACKUP = Paths.get(MyConfig.MANGAROCK_DB_BACKUP);
    public static final Path MANGAROCK_INPUT_DB = Paths.get(MyConfig.MANGAROCK_INPUT_DB);
    public static final Path MANGAROCK_INPUT_DIR = Paths.get(MyConfig.MANGAROCK_INPUT_DIR);
    public static final Path MANGA_DIR = Paths.get(MyConfig.MANGA_DIR);
    public static final Path SAMROCK_DB = Paths.get(MyConfig.SAMROCK_DB);
    public static final Path THUMBS_FOLDER = Paths.get(MyConfig.SAMROCK_THUMBS_DIR);
    public static final Path DATA_FOLDER = Paths.get(MyConfig.MANGA_DATA_DIR);

    public static final int MANGA_DIR_NAMECOUNT = MANGA_DIR.getNameCount();

    public static final Path CHAPTERS_DATA_FILE;
    public static final  int THREAD_COUNT = 4;
    public static final  int MAX_FILE_NUMBER;
    public static final  boolean DONT_SKIP_NUMBER_MISSINGS_CHECK ;
    public static final  boolean DONT_SKIP_DOUBLE_PAGE_CHECK ;
    public static final  boolean DONT_SKIP_PAGE_SIZE_CHECK ;
    public static final  boolean DONT_SKIP_FISHY_CHECK ;

    static {
        Month currentMonth = LocalDate.now().getMonth();

        for (Month m : Month.values()) {
            if(m == currentMonth)
                continue;

            Path p = CACHE_DIR.resolve(m.toString().toLowerCase());

            if(Files.exists(p)) {
                try {

                    FileWalkResult fr = FilesWalker.listDirsFiles(p);
                    for (Path file : fr.files)
                        Files.delete(file);

                    Collections.sort(fr.dirs, Comparator.comparing(Path::getNameCount).reversed());
                    for (Path file : fr.dirs)
                        Files.delete(file);
                } catch (IOException e) {
                    Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, "failed to delete: "+ p, e);
                }
            }
        }

        String s = lookup("CHAPTERS_DATA_FILE");
        if(s == null)
            CHAPTERS_DATA_FILE = null;
        else {
            Path p = Paths.get(s);
            if(!Files.isRegularFile(p)) {
                Logger.getLogger(Utils.class.getName()).info(red(Files.exists(p) ? "not a file: " : "file not found: ")+p);
                p = null;
            }
            CHAPTERS_DATA_FILE = p;
        }
        
        MAX_FILE_NUMBER = Integer.parseInt(lookup("MAX_FILE_NUMBER"));
        DONT_SKIP_DOUBLE_PAGE_CHECK = !lookup("SKIP_DOUBLE_PAGE_CHECK").equalsIgnoreCase("true"); 
        DONT_SKIP_NUMBER_MISSINGS_CHECK = !lookup("SKIP_NUMBER_MISSINGS_CHECK").equalsIgnoreCase("true");
        DONT_SKIP_PAGE_SIZE_CHECK = !lookup("SKIP_PAGE_SIZE_CHECK").equalsIgnoreCase("true");
        DONT_SKIP_FISHY_CHECK = !lookup("SKIP_FISHY_CHECK").equalsIgnoreCase("true");

        Function<Boolean,String > c = b -> b ? green("NO") : red("YES"); 

        Logger l = Logger.getLogger(Utils.class.getName());
        l.info("CHAPTERS_DATA_FILE: "+yellow(CHAPTERS_DATA_FILE));
        l.info("MAX_FILE_NUMBER: "+yellow(MAX_FILE_NUMBER));
        l.info("SKIP_DOUBLE_PAGE_CHECK: "+c.apply(DONT_SKIP_DOUBLE_PAGE_CHECK));
        l.info("SKIP_NUMBER_MISSINGS_CHECK: "+c.apply(DONT_SKIP_NUMBER_MISSINGS_CHECK));
        l.info("SKIP_PAGE_SIZE_CHECK: "+c.apply(DONT_SKIP_PAGE_SIZE_CHECK));
        l.info("SKIP_FISHY_CHECK: "+c.apply(DONT_SKIP_FISHY_CHECK));
        
        Runtime.getRuntime().addShutdownHook(new Thread(Utils::finish));
    }
    
    private static void finish() {
        if(Files.exists(CACHE_DIR)) {
            try {
                Files.walk(CACHE_DIR)
                .filter(Files::isDirectory)
                .sorted((f1, f2) -> Integer.compare(f2.getNameCount(), f1.getNameCount()))
                .map(Path::toFile)
                .forEach(File::delete);
            } catch (IOException e) {}
        }
        Logger.getLogger(Utils.class.getName()).info(yellow("\nfinished"));
    }
    
    public static boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(null, msg) == JOptionPane.YES_OPTION;
    }
    public static Path subpath(Path p) {
        return p == null  || !p.startsWith(MANGA_DIR) ? p : p.subpath(MANGA_DIR_NAMECOUNT, p.getNameCount());
    }

    /**
     * move given file or folder to backupRoot
     * @param file
     * @param backupRoot
     * @return
     * @throws IOException
     */
    public static Path backupMove(Path file, Path backupRoot) throws IOException {
        if(Files.notExists(file))
            return null;

        Path backup; 

        if(file.startsWith(MANGA_DIR))
            backup = backupRoot.resolve(Utils.subpath(file));
        else if(file.getNameCount() > 2)
            backup = backupRoot.resolve(file.subpath(file.getNameCount() - 2, file.getNameCount()));
        else 
            backup = backupRoot.resolve(file.getFileName());

        Files.createDirectories(backup.getParent());

        if(Files.exists(backup))
            backup = Paths.get(backup+""+System.currentTimeMillis());
        Files.move(file,backup, StandardCopyOption.REPLACE_EXISTING);

        return backup;
    }
    public static Path createBackupFolder(Class<?> cls) {
        Month currentMonth = LocalDate.now().getMonth();
        try {
            Path path = CACHE_DIR.resolve(currentMonth.toString().toLowerCase()).resolve(cls.getCanonicalName());
            Files.createDirectories(path);

            return path;
        } catch (IOException e) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE,"failed to create dirs ", e); 
            throw new RuntimeException("failed to create dirs ", e);
        }
    }
    public static void saveErrors(List<Errors> list, Path savePath) {
        list.removeIf(e -> !e.hasError());

        if(!list.isEmpty()) {
            StringBuilder sb = new StringBuilder(list.stream()
                    .flatMap(e -> e.values().stream())
                    .flatMap(List::stream)
                    .mapToInt(s -> s instanceof String ? ((String)s).length() : 0)
                    .sum()+100);

            list.stream().filter(p -> p.getFullPath().getNameCount() < 3).forEach(e -> {
                sb.append("\n--------------------------------\n")
                .append(e.getFullPath()).append('\n');
                e.appendTo(sb).append('\n');
            });

            sb.append('\n');

            Map<Path, Map<Path, List<Errors>>> map = list.stream()
                    .filter(p -> p.getFullPath().getNameCount() > 2)
                    .collect(Collectors.groupingBy(p -> p.getFullPath().subpath(0, p.getFullPath().getNameCount() - 2), Collectors.groupingBy(p -> p.getFullPath().getName(p.getFullPath().getNameCount() - 2))));

            char[] c2 = new char[2];
            char[] c4 = new char[4];
            char[] c6 = new char[6];

            Arrays.fill(c2, ' ');
            Arrays.fill(c4, ' ');
            Arrays.fill(c6, ' ');

            map.forEach((fullpath,map2) -> {
                sb.append("\n--------------------------------\n")
                .append(fullpath);
                map2.forEach((path, list2) -> {
                    sb.append(c2).append(path).append('\n');
                    list2.forEach(e -> e.forEach((key, values) -> {
                        sb.append(c4).append(key).append('\n');
                        values.forEach(o -> sb.append(c6).append(o).append('\n'));
                    }));
                });
                sb.append('\n');
            });
            try {
                Files.write(savePath, sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e1) {
                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, "failed to write: "+savePath, e1);
            }
        }
    }
    public  static void writeList(Collection<?> list, Path path) {
        if(list == null || list.isEmpty())
            return ;
        try {
            Files.write(path, list.stream().reduce(new StringBuilder(), (s,t) -> s.append(t).append('\n'), StringBuilder::append).toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Logger.getLogger(Utils.class.getName()).info("backup: "+path.getFileName());    
        } catch (Exception e) {
            Logger.getLogger(Utils.class.getName()).log(Level.WARNING, "failed backup: "+path.getFileName(), e);
        }
    }
    public static ExecutorService runOnExecutorService(List<Runnable> tasks) {
        if(tasks == null || tasks.isEmpty())
            throw new IllegalArgumentException("no tasks tasks found: "+tasks);

        if(tasks.size() == 1) 
            tasks.get(0).run();
        else {
            ExecutorService ex = Executors.newFixedThreadPool(tasks.size() <  THREAD_COUNT ? tasks.size() : THREAD_COUNT);

            for (Runnable p : tasks)
                ex.execute(p);
            
            return ex;
        }
        return null;
    }
    public static void shutdownAndWait(ExecutorService ex) {
        ex.shutdown();
        while(!ex.isTerminated()) {}
    }
}
