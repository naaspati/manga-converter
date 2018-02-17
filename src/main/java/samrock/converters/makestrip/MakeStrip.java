package samrock.converters.makestrip;
import static sam.console.ansi.ANSI.red;
import static samrock.converters.extras.Utils.DONT_SKIP_DOUBLE_PAGE_CHECK;
import static samrock.converters.extras.Utils.DONT_SKIP_FISHY_CHECK;
import static samrock.converters.extras.Utils.DONT_SKIP_PAGE_SIZE_CHECK;
import static samrock.converters.extras.Utils.subpath;

import java.awt.Graphics2D;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import sam.myutils.fileutils.FilesUtils;
import sam.myutils.onmany.OneOrMany;
import samrock.converters.extras.ConvertTask;
import samrock.converters.extras.Errors;
import samrock.converters.extras.Utils;
import samrock.converters.filechecker.CheckedFile;

/**
 * to convert a double paged manga (reported wrong or is actually a double paged and want to convert is as it is, the set MangaConvert.forceDoublePagedConvert = true;) 
 * 
 *
 */
public class MakeStrip implements Runnable {
    public static final AtomicBoolean CANCEL = new AtomicBoolean();
    public static final Path TEMP_DIR = Utils.createBackupFolder(MakeStrip.class);

    static {
        try {
            Files.createDirectories(TEMP_DIR);
        } catch (IOException e) {
            throw new RuntimeException("failed to create: "+TEMP_DIR, e);
        }
    }

    private final ConvertTask task;
    private final MakeStripFinished finish;
    private Errors errors;
    private OneOrMany<Path> convertedFiles;

    /**
     * 
     * @param files 
     * @param f
     * @throws IOException
     */
    public MakeStrip(ConvertTask task, MakeStripFinished finish) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(finish);

        this.task = task;
        this.finish = finish;
    }
    
    public OneOrMany<Path> getConvertedFiles() {
        return convertedFiles;
    }
    public ConvertTask getTask() {
        return task;
    }

    @Override
    public void run() {
        if(CANCEL.get()) return;

        errors = new Errors(task.getSource());
        convertedFiles = new OneOrMany<>();

        try {
            start();
        } catch (NullPointerException|OutOfMemoryError e) {
            getErrors().addGeneralError(e, "Error while converting");
        }

        if(CANCEL.get())
            getErrors().setCancelled();

        finish.finish(this);
    }
    private boolean imageError = false;
    ArrayList<BufferedImage> images;

    public void setImageError() {
        this.imageError = true;
        images.clear();
        convertedFiles.clear();
    }
    private void start() {
        if(CANCEL.get()) return;

        int currentHeight = 0;
        images = new ArrayList<>();

        List<Path> doublePages = DONT_SKIP_DOUBLE_PAGE_CHECK ? new ArrayList<>() : null;

        for (CheckedFile t : task.getFiles()) {
            if(CANCEL.get()) return;

            if(t == null)
                continue;
            Path path = t.getPath();
            
            try(InputStream is = Files.newInputStream(path, StandardOpenOption.READ)) {
                BufferedImage img = ImageIO.read(is);
                if(!imageError)
                    images.add(img);

                //check non images and nullPointers
                int w = img.getWidth();
                int h = img.getHeight();

                if(DONT_SKIP_FISHY_CHECK && h > 65500/3){
                    System.out.println(red("something is fishy, image height found to be: "+h)+"\t"+task.getSource());
                    Files.write(Paths.get("fish.txt"), ("something is fishy, image height found to be: "+h+"\t"+task.getSource()).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    FilesUtils.openFile(new File("fish.txt"));
                    setImageError();
                    System.exit(0);
                }

                currentHeight += h;

                if(DONT_SKIP_PAGE_SIZE_CHECK && (w < 100 || h < 100)){
                    getErrors().addImageSizeError(path.subpath(path.getNameCount() - 2, path.getNameCount()), w, h);
                    setImageError(); 
                }
                if(DONT_SKIP_DOUBLE_PAGE_CHECK && w > 1000)
                    doublePages.add(subpath(path));

                if(!imageError && currentHeight > 65500) {
                    if(!convert(images.subList(0, images.size() - 1)))
                        setImageError();
                    currentHeight = h;
                }
            } catch (IOException|NullPointerException|IllegalArgumentException e) {
                getErrors().addImageError(e, "Image Error", path.subpath(path.getNameCount() - 2, path.getNameCount()));
                setImageError();
            }
        }

        int doublePageCount = doublePages == null ? 0 : doublePages.size();

        if(doublePageCount > 0  && doublePageCount > task.getFiles().length  - task.getFiles().length/4){
            getErrors().addDoublePagesError(doublePages);
            return;
        }
        if(!imageError && !images.isEmpty())
            imageError = convert(images);
    }
    private boolean convert(List<BufferedImage> images) {
        if(CANCEL.get()) return false;

        int height = 0, width = 0;
        for (BufferedImage m : images) {
            width = Math.max(width, m.getWidth());
            height += m.getHeight();
        }
        
        Path temp = createStrip(images, width, height);

        if(temp != null){
            convertedFiles.add(temp);
            return true;
        }
        images.clear();
        return false;

    }
    private Path createStrip(List<BufferedImage> images, int width, int height) {
        if(CANCEL.get()) return null;

        Path temp = null;
        try {
            temp = Files.createTempFile(TEMP_DIR, task.getSource().getFileName().toString(), null);
        } catch (IOException e) {
            getErrors().addImageError(e, "failed to create temp file: ", task.getSource().getFileName());
            return null;
        }

        BufferedImage finalImage = new BufferedImage(width, height, images.get(0).getType());
        Graphics2D g = finalImage.createGraphics();

        int totalHeight = 0;
        for (BufferedImage m : images) {
            g.drawImage(m, (width - m.getWidth()) / 2, totalHeight, null);
            totalHeight += m.getHeight();
            m.flush();    
        }
        g.dispose();

        try(OutputStream os = Files.newOutputStream(temp)) {
            ImageIO.write(finalImage, "jpeg", os);
            return temp;
        } catch (IOException e) {
            getErrors().addImageError(e, "Unable to save Image");
        }
        return null;
    }

    public Errors getErrors() {
        return errors;
    }
}
