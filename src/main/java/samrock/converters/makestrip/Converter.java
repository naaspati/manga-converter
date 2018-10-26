package samrock.converters.makestrip;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static samrock.converters.extras.Utils.subpath;

import java.awt.EventQueue;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import sam.collection.OneOrMany;
import sam.console.ANSI;
import sam.logging.MyLoggerFactory;
import sam.manga.samrock.chapters.ChapterWithMangaId;
import sam.manga.samrock.converter.ConvertChapter;
import samrock.converters.extras.ConvertTask;
import samrock.converters.extras.Errors;
import samrock.converters.extras.Progressor;
import samrock.converters.extras.SourceTarget;
import samrock.converters.extras.Utils;
import samrock.converters.filechecker.FilesChecker;
import samrock.converters.filechecker.ResultOrErrors;

public class Converter {

    private static final Path BACKUP_DIR = Utils.createBackupFolder(Converter.class);
    private static final Path IMAGES_BACKUP_DIR = BACKUP_DIR.resolve("images_backup");

    private final Progressor progress;
    private String totalSign;
    private final List<Errors> errorsList = Collections.synchronizedList(new ArrayList<>());
    private final Logger logger = MyLoggerFactory.logger(Converter.class.getSimpleName());

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
                logger.info(ANSI.createBanner("NOTHING TO "+label));
                return null;
            }
            return list;
        } catch (IOException e) {
            logger.log(WARNING, "Failed checkDirs()", e);
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
                logger.info(re.getErrors().toString());
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
                logger.info("moved: " + subpath(st.getSource()) + ANSI.yellow(" -> ") +subpath(st.getTarget()));
            } catch (IOException e) {
                logger.log(SEVERE, "failed to move: "+st, e);
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
        
        if(tasks.isEmpty()) {
            logger.info(ANSI.green("nothing to convert"));
            progress.setCompleted();
            return;
        }
        
        int nThreads = tasks.size() <  Utils.THREAD_COUNT ? tasks.size() : Utils.THREAD_COUNT;
        
        ThreadPool pool = new ThreadPool(nThreads);
        tasks.forEach(c -> pool.submit(new MakeStrip(c)));

        progress.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if(pool.isTerminated())
                    return;

                MakeStrip.CANCEL.set(true);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {}
                pool.shutdownNow();
                while(!pool.isTerminated()) {}
            }
        });
        pool.shutdown();
        
        try {
            pool.awaitTermination(2, TimeUnit.DAYS);
        } catch (InterruptedException e1) {
            logger.log(WARNING, "startConversion() was intruppted: ", e1);
        }
        progress.setCompleted();
    }
    
    private class ThreadPool extends ThreadPoolExecutor {
        public ThreadPool(int nThreads) {
            super(nThreads, nThreads, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        }
        @SuppressWarnings("unchecked")
        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            
            if(r != null && r instanceof Future) {
                MakeStripResult ms;
                try {
                    ms = ((Future<MakeStripResult>)r).get();
                } catch (InterruptedException | ExecutionException e1) {
                    logger.log(WARNING, "thread Interrupted : " + Thread.currentThread().getName(), e1);
                    return;
                }
                
                Errors errors = ms.errors;
                OneOrMany<Path> convertedFiles = ms.convertedFiles;
                ConvertTask task = ms.task;
                
                EventQueue.invokeLater(() -> {
                    progress.setString("Converted: "+errors.getSubpath());
                    progress.setTitle(progress.getCurrentProgress() + totalSign);
                    progress.increaseBy1();
                });
                
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
                    logger.warning(errors.getSubpath()+"\n"+errors);
                }
                return;
            }
            EventQueue.invokeLater(progress::increaseBy1);
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
    }
    private Path backup(Path p) throws IOException {
        return Utils.backupMove(p, IMAGES_BACKUP_DIR);
    }
}
