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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import sam.console.ansi.ANSI;
import sam.myutils.onmany.OneOrMany;
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

    private final List<SourceTarget> convertingTasks;
    private final Progressor progress;
    private final String totalSign;
    private final List<Errors> errorsList = Collections.synchronizedList(new ArrayList<>());

    public Converter(Collection<SourceTarget> convertingTasks, Progressor progress) {
        Objects.requireNonNull( convertingTasks);
        if(convertingTasks.isEmpty())
            throw new IllegalArgumentException("no convertingTasks found");

        this.progress = progress;
        this.convertingTasks = new LinkedList<>(convertingTasks);
        totalSign = " / ".concat(String.valueOf(convertingTasks.size()));

        if(convertingTasks.isEmpty()){
            printError("nothing to convert");
            progress.setFailed();
            return;
        }
    }

    public void convert() {
        List<ConvertTask> list = check("CONVERT");
        if(list == null)
            return;

        start(list);
        saveErrors();
    }
    private void saveErrors() {
        Utils.saveErrors(errorsList, BACKUP_DIR.resolve("converting-errors.txt"));
    }
    private List<ConvertTask> check(String string) {
        try {
            List<ConvertTask> list = checkDirs();
            if(list.isEmpty()) {
                progress.setReset("NOTHING TO "+string);
                System.out.println(ANSI.createBanner("NOTHING TO "+string));
                return null;
            }
            return list;
        } catch (IOException e) {
            System.out.println("Failed checkDirs()");
            e.printStackTrace();
        }
        return null;
    }

    public void move() {
        List<ConvertTask> list = check("MOVE");
        if(list == null)
            return;

        for (SourceTarget st : convertingTasks) {
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
    private List<ConvertTask> checkDirs() throws IOException {
        progress.setReset("Checking Dirs");
        progress.resetProgress();
        progress.setMaximum(convertingTasks.size());

        List<ConvertTask> list = new ArrayList<>();

        for (SourceTarget st : convertingTasks) {
            progress.increaseBy1();

            ResultOrErrors re = FilesChecker.check(st.getSource(), st.getTarget());
            if(re.hasErrors()) {
                errorsList.add(re.getErrors());
                System.out.println(re.getErrors());
            }
            else
                list.add(new ConvertTask(st, re.getFiles()));
        }
        return list;
    }
    private void start(List<ConvertTask> tasks) {
        progress.setReset("Converting");
        progress.resetProgress();
        progress.setMaximum(tasks.size());
        progress.setTitle("0 ".concat(totalSign));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        AtomicBoolean finished = new AtomicBoolean();

        progress.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if(finished.get())
                    System.exit(0);

                MakeStrip.CANCEL.set(true);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {}
                executor.shutdownNow();
                while(!executor.isTerminated()) {}
            }
        });

        ConverterFinish finisher = this::progress;
        tasks.stream()
        .map(temp -> new MakeStrip(temp, finisher))
        .forEach(executor::execute);

        executor.shutdown();
        
        while(!executor.isTerminated()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {}
        }

        progress.setCompleted();
        finished.set(true);
    }

    private void progress(Errors errors, ConvertTask task, OneOrMany<Path> convertedFiles) {
        synchronized(progress) {
            progress.setString("Converted: "+errors.getSubpath());
            progress.setTitle(progress.getCurrentProgress() + totalSign);
            progress.increaseBy1();
        }

        if(!errors.hasError() && !convertedFiles.isEmpty()){
            if(convertedFiles.isSingleValued())
                move(convertedFiles.getValue(), task.getTarget().resolveSibling(task.getTarget().getFileName()+".jpeg"), errors);
            else {
                int n = 1; 
                for (Path p : convertedFiles.getValues())
                    move(p, task.getTarget().resolveSibling(task.getTarget().getFileName()+" - "+(n++)+".jpeg"), errors);    
            }
            if(!errors.hasError()) {
                try {
                    backup(task.getSource());
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
    private void move(Path src, Path target, Errors errors) {
        try {
            Path backup = backup(target);
            errors.addGarbagedError(Utils.subpath(target), Utils.subpath(backup));
        } catch (IOException e) {
            errors.addMoveFailed(e, "Failed to garbage", target);
            return;
        }
        try {
            Files.move(src, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            errors.addMoveFailed(e, "Failed to move", "src: ", src, " | target: ",target);
        }
    }
    private Path backup(Path p) throws IOException {
        return Utils.backupMove(p, IMAGES_BACKUP_DIR);
    }

}
