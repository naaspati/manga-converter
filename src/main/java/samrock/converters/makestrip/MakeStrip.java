package samrock.converters.makestrip;
import static sam.console.ANSI.red;
import static samrock.converters.extras.Utils.DONT_SKIP_DOUBLE_PAGE_CHECK;
import static samrock.converters.extras.Utils.DONT_SKIP_FISHY_CHECK;
import static samrock.converters.extras.Utils.DONT_SKIP_PAGE_SIZE_CHECK;
import static samrock.converters.extras.Utils.subpath;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import sam.collection.OneOrMany;
import sam.io.fileutils.FileOpener;
import sam.logging.MyLoggerFactory;
import samrock.converters.extras.ConvertTask;
import samrock.converters.extras.Errors;
import samrock.converters.extras.Utils;
import samrock.converters.filechecker.CheckedFile;

/**
 * to convert a double paged manga (reported wrong or is actually a double paged and want to convert is as it is, the set MangaConvert.forceDoublePagedConvert = true;) 
 * 
 *
 */
public class MakeStrip implements Callable<MakeStripResult> {
    public static final AtomicBoolean CANCEL = new AtomicBoolean();

    public static final Path TEMP_DIR = Utils.createBackupFolder(MakeStrip.class);
    private static final int MAX_IMAGE_HEIGHT = 65500;

    static {
        try {
            Files.createDirectories(TEMP_DIR);
        } catch (IOException e) {
            MyLoggerFactory.logger(MakeStrip.class.getName()).log(Level.SEVERE, "failed to create: "+TEMP_DIR, e);
            throw new RuntimeException("failed to create: "+TEMP_DIR, e);
        }
    }
    private final ConvertTask task;
    private Errors errors;

    /**
     * 
     * @param files 
     * @param f
     * @throws IOException
     */
    public MakeStrip(ConvertTask task) {
        Objects.requireNonNull(task);
        this.task = task;
    }

    @Override
    public MakeStripResult call() {
        if(CANCEL.get()) return null;

        errors = new Errors(task.getSource());

        try {
            if(CANCEL.get()) {
                getErrors().setCancelled();
                return new MakeStripResult(task, errors, null);
            }
            return new MakeStripResult(task, errors, call2());
        } catch (NullPointerException|OutOfMemoryError e) {
            getErrors().addGeneralError(e, "Error while converting");
            return new MakeStripResult(task, errors, null);
        } finally {
            if(images != null) {
                images.clear();
                images = null;
            }
        }
    }

    private boolean imageError = false;
    private LinkedList<BufferedImage> images;
    private OneOrMany<Path> result;

    public void setImageError() {
        this.imageError = true;
        if(images != null)
            images.clear();
        images = null;
        if(result != null) {
            result.clear();
            result = null;
        }
    }
    private OneOrMany<Path> call2() {
        if(CANCEL.get()) return null;

        images = new LinkedList<>();
        result = new OneOrMany<>();

        List<Path> doublePages = DONT_SKIP_DOUBLE_PAGE_CHECK ? new ArrayList<>() : null;
        int width = 0;
        int height = 0;

        for (CheckedFile t : task.getFiles()) {
            if(CANCEL.get()) return null;

            if(t == null)
                continue;
            Path path = t.getPath();
            
            BufferedImage img = null;

            try(InputStream is = Files.newInputStream(path, StandardOpenOption.READ)) {
                img = ImageIO.read(is);

                //check non images and nullPointers
                int w = img.getWidth();
                int h = img.getHeight();

                if(DONT_SKIP_FISHY_CHECK && h > MAX_IMAGE_HEIGHT/3){
                    MyLoggerFactory.logger(MakeStrip.class.getName()).severe(red("something is fishy, image height found to be: "+h)+"\t"+task.getSource());
                    Files.write(Paths.get("fish.txt"), ("something is fishy, image height found to be: "+h+"\t"+task.getSource()).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    FileOpener.openFile(Paths.get("fish.txt").toFile());
                    setImageError();
                    System.exit(0);
                }
                if(DONT_SKIP_PAGE_SIZE_CHECK && (w < 100 || h < 100)){
                    getErrors().addImageSizeError(path.subpath(path.getNameCount() - 2, path.getNameCount()), w, h);
                    setImageError(); 
                }
                if(DONT_SKIP_DOUBLE_PAGE_CHECK && w > 1000)
                    doublePages.add(subpath(path));

                if(height + h > MAX_IMAGE_HEIGHT) {
                    if(!imageError)
                        convert(width, height);
                    width = 0;
                    height = 0;
                }

                width = Math.max(w, width);
                height += h;

                if(!imageError)
                    images.add(img);
            } catch (IOException|NullPointerException|IllegalArgumentException e) {
                getErrors().addImageError(e, "Image Error", path.subpath(path.getNameCount() - 2, path.getNameCount()), img != null ? "img type: "+ img.getType() : "");
                setImageError();
            }
        }

        int doublePageCount = doublePages == null ? 0 : doublePages.size();

        if(doublePageCount > 0  && doublePageCount > task.getFiles().length  - task.getFiles().length/4){
            getErrors().addDoublePagesError(doublePages);
            return null;
        }
        if(!imageError && !images.isEmpty())
            convert(width, height);

        return result;
    }

	private boolean convert(int width, int height) {
        if(CANCEL.get()) return false;

        Path temp = createStrip(width, height);

        if(temp != null){
            result.add(temp);
            return true;
        }

        setImageError();
        return false;

    }
    private Path createStrip(final int width, final int height) {
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

        while(!images.isEmpty()) {
            if(CANCEL.get()) return null;

            BufferedImage m = images.removeFirst();

            g.drawImage(m, (width - m.getWidth()) / 2, totalHeight, null);
            totalHeight += m.getHeight();
            m.flush();
        }
        g.dispose();

        if(totalHeight != height) {
            errors.addImageError(null, String.format("converted height(%s) is not equal to expected height(%s)", totalHeight, height));
            return null;
        }

        try(OutputStream os = Files.newOutputStream(temp)) {
            ImageIO.write(finalImage, "jpeg", os);
            return temp;
        } catch (IOException e) {
            getErrors().addImageError(e, "Unable to save Image: size");
        }
        return null;
    }

    public Errors getErrors() {
        return errors;
    }
}
