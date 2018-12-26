package samrock.converters.extras;

import static sam.console.ANSI.yellow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;

import javax.swing.JOptionPane;

import sam.config.MyConfig;
import sam.io.fileutils.FilesWalker;
import sam.io.fileutils.FilesWalker.FileWalkResult;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsPath;
public class Utils {
    private Utils() {}
    
    public static final Path SELF_DIR = MyUtilsPath.selfDir();
    public static final Path APP_DATA = SELF_DIR.resolve("app_data");
    public static final Path TEMP_DIR = MyUtilsPath.TEMP_DIR;
    public static final Path CACHE_DIR = TEMP_DIR.resolve("cache_"+MyUtilsPath.pathFormattedDateTime());
    
    public static final Path MANGAROCK_DB_BACKUP = Paths.get(MyConfig.MANGAROCK_DB_BACKUP);
    public static final Path MANGAROCK_INPUT_DB = Paths.get(MyConfig.MANGAROCK_INPUT_DB);
    public static final Path MANGAROCK_INPUT_DIR = Paths.get(MyConfig.MANGAROCK_INPUT_DIR);
    public static final Path SAMROCK_DB = Paths.get(MyConfig.SAMROCK_DB);
	public static final Path MANGA_DIR = Paths.get(MyConfig.MANGA_DIR);
   
    static {
    	MyUtilsException.hideError(() -> Files.createDirectories(APP_DATA));
    	
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
                    MyLoggerFactory.logger(Utils.class).log(Level.SEVERE, "failed to delete: "+ p, e);
                }
            }
        }
        
        //FIXME is finish really required
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
        MyLoggerFactory.logger(Utils.class).info(yellow("\nfinished"));
    }
    
    public static boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(null, msg) == JOptionPane.YES_OPTION;
    }
    
    private static final int MANGA_DIR_COUNT = MANGA_DIR.getNameCount();
    private static final int SELF_DIR_COUNT = SELF_DIR.getNameCount();
    /**
     * for debugging perpose
     * @param p
     * @return
     */
    public static Path subpath(Path p) {
    	if(p.startsWith(SELF_DIR))
    		return p.subpath(SELF_DIR_COUNT, p.getNameCount());
    	
    	if(p.startsWith(MANGA_DIR))
    		return p.subpath(MANGA_DIR_COUNT, p.getNameCount());
    	
    	return p;
    }
}
