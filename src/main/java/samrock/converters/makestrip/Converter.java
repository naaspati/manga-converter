package samrock.converters.makestrip;

import static samrock.converters.extras.Utils.printError;
import static samrock.converters.extras.Utils.println;
import static samrock.converters.extras.Utils.subpath;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import sam.console.ansi.ANSI;
import sam.manga.newsamrock.chapters.ChapterWithMangaId;
import sam.manga.newsamrock.converter.ConvertChapter;
import sam.myutils.onmany.OneOrMany;
import samrock.converters.extras.ConvertTask;
import samrock.converters.extras.Errors;
import samrock.converters.extras.Progressor;
import samrock.converters.extras.SourceTarget;
import samrock.converters.extras.Utils;
import samrock.converters.filechecker.FilesChecker;
import samrock.converters.filechecker.ResultOrErrors;

public class Converter implements MakeStripFinished {

    private static final Path BACKUP_DIR = Utils.createBackupFolder(Converter.class);
    private static final Path IMAGES_BACKUP_DIR = BACKUP_DIR.resolve("images_backup");

    private final Progressor progress;
    private String totalSign;
    private final List<Errors> errorsList = Collections.synchronizedList(new ArrayList<>());

    public Converter(Progressor progress) {
        this.progress = progress;
    }

    public List<ChapterWithMangaId> convert(Collection<ConvertChapter> chapters) {
        progress.setReset("Checking Dirs");
        progress.resetProgress();
        progress.setMaximum(chapters.size());

        List<ConvertTask> list = check(chapters, "CONVERT", ConvertChapter::getSource, ConvertChapter::getTarget);

        if(list == null || list.isEmpty()) {
            saveErrors();
            return null;
        }
        totalSign = " / "+list.size();
        startConversion(list);
        saveErrors();

        List<ChapterWithMangaId> chs = new LinkedList<>();

        for (ConvertTask ct : list) {
            OneOrMany<Path> result = ct.getResult();
            if(result != null && !result.isEmpty()) {
                ConvertChapter cc = (ConvertChapter) ct.getUserObject();
                if(result.isSingleValued())
                    chs.add(new ChapterWithMangaId(cc.getMangaId(), cc.getNumber(), result.getValue().getFileName().toString()));
                else {
                    for (Path p : result) 
                        chs.add(new ChapterWithMangaId(cc.getMangaId(), cc.getNumber(), p.getFileName().toString()));
                }                
            }
        }
        return chs;
    }
    private <E> List<ConvertTask> check(Collection<E> data, String label, Function<E, Path> sourceGetter, Function<E, Path> targetGetter) {
        try {
            List<ConvertTask> list = checkDirs(data, sourceGetter, targetGetter);
            if(list.isEmpty()) {
                progress.setReset("NOTHING TO "+label);
                System.out.println(ANSI.createBanner("NOTHING TO "+label));
                return null;
            }
            return list;
        } catch (IOException e) {
            System.out.println("Failed checkDirs()");
            e.printStackTrace();
        }
        return null;
    }

    private <E> List<ConvertTask> checkDirs(Iterable<E> data, Function<E, Path> sourceGetter, Function<E, Path> targetGetter) throws IOException {
        List<ConvertTask> list = new ArrayList<>();

        for (E e : data) {
            progress.increaseBy1();
            Path src = sourceGetter.apply(e);
            Path trgt = targetGetter.apply(e);

            ResultOrErrors re = FilesChecker.check(src, trgt);

            if(re.hasErrors()) {
                errorsList.add(re.getErrors());
                System.out.println(re.getErrors());
            }
            else 
                list.add(new ConvertTask(src, trgt, re.getFiles(), e));
        }
        return list;
    }

    private void saveErrors() {
        Utils.saveErrors(errorsList, BACKUP_DIR.resolve("converting-errors.txt"));
    }
    public void move(List<SourceTarget> data) throws IOException {
        List<ConvertTask> list = check(data,  "MOVE", SourceTarget::getSource, SourceTarget::getTarget);

        if(list == null || list.isEmpty())
            return;

        for (ConvertTask st : list) {
            try {
                if(st.getSource().equals(st.getTarget()) || Files.isSameFile(st.getSource(),st.getTarget()))
                    continue;
                backup(st.getTarget());
                Files.move(st.getSource(), st.getTarget());
                println("moved: ",subpath(st.getSource())," -> ",subpath(st.getTarget()));
            } catch (IOException e) {
                printError(e, "failed to move: "+st);
            }
        }
        progress.setCompleted();
        saveErrors();
    }
    private void startConversion(List<ConvertTask> tasks) {
        progress.setReset("Converting");
        progress.resetProgress();
        progress.setMaximum(tasks.size());
        progress.setTitle("0 ".concat(totalSign));

        ExecutorService executor = Utils.runOnExecutorService(tasks.stream().map(t -> new MakeStrip(t, this)).collect(Collectors.toList()));

        if(executor != null) {
            AtomicBoolean finished = new AtomicBoolean();

            progress.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if(finished.get())
                        return;

                    MakeStrip.CANCEL.set(true);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {}
                    executor.shutdownNow();
                    while(!executor.isTerminated()) {}
                }
            });
            Utils.shutdownAndWait(executor);
            finished.set(true);
        }
        progress.setCompleted();
    }

    @Override
    public void finish(MakeStrip mtask) {
        Errors errors = mtask.getErrors();
        OneOrMany<Path> convertedFiles = mtask.getConvertedFiles();
        ConvertTask task = mtask.getTask();

        synchronized(progress) {
            progress.setString("Converted: "+errors.getSubpath());
            progress.setTitle(progress.getCurrentProgress() + totalSign);
            progress.increaseBy1();
        }

        if(!errors.hasError() && !convertedFiles.isEmpty()){
            OneOrMany<Path> result = new OneOrMany<>();
            if(convertedFiles.isSingleValued()) 
                result.add(move(convertedFiles.getValue(), task.getTarget().resolveSibling(task.getTarget().getFileName()+".jpeg"), errors));
            else {
                int n = 1; 
                for (Path p : convertedFiles.getValues())
                    result.add(move(p, task.getTarget().resolveSibling(task.getTarget().getFileName()+" - "+(n++)+".jpeg"), errors));    
            }
            if(!errors.hasError()) {
                try {
                    backup(task.getSource());
                    task.setResult(result);
                } catch (IOException e) {
                    errors.addGeneralError(e, "failed to move to backup: ",task.getSource());
                }
            }
        }
        if(errors.hasError()) {
            errorsList.add(errors);
            System.out.println(errors.getSubpath()+"\n"+errors);
        }
    }
    private Path move(Path src, Path target, Errors errors) {
        try {
            Path backup = backup(target);
            if(backup != null)
                errors.addGarbagedError(Utils.subpath(target), Utils.subpath(backup));
        } catch (IOException e) {
            errors.addMoveFailed(e, "Failed to garbage", target);
            return null;
        }
        try {
            return  Files.move(src, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            errors.addMoveFailed(e, "Failed to move", "src: ", src, " | target: ",target);
        }
        return null;
    }
    private Path backup(Path p) throws IOException {
        return Utils.backupMove(p, IMAGES_BACKUP_DIR);
    }

}
