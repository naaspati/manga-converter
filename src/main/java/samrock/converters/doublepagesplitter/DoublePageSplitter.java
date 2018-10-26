package samrock.converters.doublepagesplitter;

import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static samrock.converters.extras.Utils.backupMove;
import static samrock.converters.extras.Utils.confirm;
import static samrock.converters.extras.Utils.subpath;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import sam.console.ANSI;
import sam.console.ANSI.FOREGROUND;
import sam.logging.MyLoggerFactory;
import sam.swing.SwingUtils;
import samrock.converters.extras.ConvertTask;
import samrock.converters.extras.Errors;
import samrock.converters.extras.Utils;
import samrock.converters.filechecker.CheckedFile;
import samrock.converters.filechecker.FilesChecker;
import samrock.converters.filechecker.ResultOrErrors;

public class DoublePageSplitter implements Runnable {
    private static final Path BACKUP_DIR = Utils.createBackupFolder(DoublePageSplitter.class);
    private static final Path TEMP_DIR = BACKUP_DIR.resolve("temp");
    private static final Logger logger = MyLoggerFactory.logger(DoublePageSplitter.class.getName()); 

    public static void splitSingleFolder() throws IOException{
        String folderPath = JOptionPane.showInputDialog(null, "Folder Path", "Double Page Splitter", JOptionPane.QUESTION_MESSAGE);

        if(folderPath == null || folderPath.isEmpty()){
            logger.warning("operation cancelled");
            return;
        }
        Path p = Paths.get(folderPath);
        ResultOrErrors re = FilesChecker.check(p, null);

        if(re.hasErrors())
            System.out.println(re.getErrors());
        else
            new DoublePageSplitter(new ConvertTask(p, re.getFiles()));
    }

    public static void batchFolderSplitting() throws IOException{
        File folder = SwingUtils.dirPathInputOptionPane("Enter path to folder");

        if(folder != null){
            File[] folders = folder.listFiles(File::isDirectory);
            if(folders.length == 0)
                logger.warning(red("no folders in folder: ")+folder);
            else{
                System.out.println(yellow("total: ")+folders.length);
                List<Errors> errors = new ArrayList<>();
                List<ConvertTask> tasks = new ArrayList<>();

                for (File f : folders) {
                    Path p = f.toPath();
                    ResultOrErrors re = FilesChecker.check(p, null); 
                    if(re.getErrors() == null)
                        tasks.add(new ConvertTask(p, re.getFiles()));
                    else
                        errors.add(re.getErrors());
                }
                if(!errors.isEmpty()) {
                    System.out.println(createBanner("bad dirs", FOREGROUND.RED, FOREGROUND.YELLOW));
                    errors.forEach(System.out::println);
                    System.out.println("\n");
                }
                if(tasks.isEmpty()) {
                    System.out.println(createBanner("NOTHING TO CONVERT", FOREGROUND.RED, FOREGROUND.YELLOW));
                } else {
                    System.out.println(createBanner("file found to convert"));

                    tasks.stream().filter(p -> p.getSource().getNameCount() < 3).forEach(System.out::println);

                    Map<Path, Map<Path, List<ConvertTask>>> map = tasks.stream()
                            .filter(p -> p.getSource().getNameCount() > 2)
                            .collect(Collectors.groupingBy(p -> p.getSource().subpath(0, p.getSource().getNameCount() - 2), Collectors.groupingBy(p -> p.getSource().getName(p.getSource().getNameCount() - 2))));

                    map.forEach((path, map2) -> {
                        System.out.println(ANSI.cyan(path));
                        map2.forEach((path2, list2) -> {
                            System.out.println("  "+yellow(path2));
                            list2.forEach(p -> System.out.println("    "+p.getSource().getFileName()));
                        });
                        System.out.println();
                    });
                    System.out.println("\n");

                    if(!errors.isEmpty() && !confirm("wish to continue? ")) {
                        System.out.println(red("CANCELLED"));
                        return;
                    }
                    ExecutorService ex =  Utils.runOnExecutorService(tasks.stream().map(DoublePageSplitter::new).collect(Collectors.toList()));
                    if(ex != null)
                        Utils.shutdownAndWait(ex);
                    System.out.println("\n"+ANSI.FINISHED_BANNER); 
                }
            }
        }
    }

    private final ConvertTask task;
    private final CheckedFile[] files;
    
    private DoublePageSplitter(ConvertTask task) {
        this.task = task;
        this.files = task.getFiles();
    }

    private static List<Errors> errors = new ArrayList<>();
    private final List<Path> splittedImages = new ArrayList<>();

    @Override
    public void run() {
        Errors err = new Errors(task.getSource());
        logger.info("splitting: "+task.getSource());
        try {
            start(err);
        } catch (IOException e) {
            err.addGeneralError(e);
        }
        if(err.hasError()) {
            System.out.println(err);
            errors.add(err);
        };
    }

    private void start(Errors err) throws IOException {
        if (files.length == 0) {
            err.addGeneralError(null, "empty folder");
            return;
        }

        for (CheckedFile cf : files) {
            if(cf == null)
                continue;

            Path f = cf.getPath();
            if(err.hasError() || !splittedImages.isEmpty()) splittedImages.clear();

            try(InputStream is = Files.newInputStream(f, StandardOpenOption.READ)) {
                BufferedImage img = ImageIO.read(is);

                //check non images and nullPointers
                int w = img.getWidth();
                int h = img.getHeight();

                //non Image check
                if(w < 100 || h < 100)
                    err.addImageSizeError(subpath(f), w, h);

                if(!err.hasError())
                    split(img, f);
            } catch (IOException|NullPointerException|NumberFormatException e) {
                err.addImageError(e, subpath(f));
            }
        }

        if(!err.hasError()) {
            Path src = task.getSource();
            try {
                backupMove(src, BACKUP_DIR.resolve("splitted-images"));
            } catch (IOException e) {
                err.addGeneralError(e, "failed to backup: ",task.getSource());
                return;
            }
            Files.createDirectories(src);

            int i = 0;
            for (Path p : splittedImages) {
                Files.move(p, src.resolve(String.valueOf(i++)));
            }
        }
    }

    private void split(BufferedImage m, Path file) throws IOException {
        if (m.getWidth() < 1000) {
            splittedImages.add(Files.copy(file, Files.createTempFile(TEMP_DIR, file.getFileName().toString(), null)));
            logger.warning("\tpossibly a single page: " + file.getFileName());
            return;
        }
        splittedImages.add(split(m, file, true));
        splittedImages.add(split(m, file, false));
    }   

    private Path split(BufferedImage m, Path file, boolean firstHalf) throws IOException {
        BufferedImage img = new BufferedImage(m.getWidth() / 2, m.getHeight(), m.getType());

        if(firstHalf)
            img.createGraphics().drawImage(m, 0, 0, img.getWidth(), img.getHeight(), m.getWidth() / 2, 0, m.getWidth(), m.getHeight(), null);
        else
            img.createGraphics().drawImage(m, 0, 0, img.getWidth(), img.getHeight(), 0, 0, m.getWidth() / 2, m.getHeight(), null);

        Path path = Files.createTempFile(TEMP_DIR, file.getName(file.getNameCount() - 2) +"_"+ file.getFileName()+"_", null);
        try(OutputStream os = Files.newOutputStream(path)) {
            ImageIO.write(img, "jpeg", os);
            return path;
        }
    }
}
