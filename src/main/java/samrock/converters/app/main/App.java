package samrock.converters.app.main;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import sam.logging.MyLoggerFactory;
import sam.myutils.System2;
import sam.nopkg.Junk;
public class App {
	private static final Logger LOGGER = MyLoggerFactory.logger(App.class);
 
	private static final List<Class<? extends CmdInit>> command_classes = Arrays.asList(ConvertConfig.class, Split.class);
	private static final Map<String, Class<? extends CmdInit>> command_map = new LinkedHashMap<>();
	

	static {
		for (Class<? extends CmdInit> c : command_classes) {
			Cmd cmd = c.getAnnotation(Cmd.class);
			
			for (String s : cmd.value()) {
				Class<?> cls = command_map.get(s);
				
				if(cls != null && cls != c)
					throw new IllegalStateException("command collision for command: "+s+", between: "+c+"  "+cls);
				command_map.put(s, c);
			}
		}  
	}

	public App(String[] args) throws Exception {
		if(args.length == 0) {
			System.out.println(red("no command specified"));
			printHelp();
		}

		if(args.length == 1) {
			if(helpOrVersion(args[0]))
			return;
		}
		
		Map<String, List<String>> map = new LinkedHashMap<>();
		List<String> current = null;
		
		for (String s : args) {
			if(command_map.containsKey(s)) {
				if(!map.containsKey(s))
					map.put(s, current = new ArrayList<>());
			} else {
				if(current == null) {
					LOGGER.info(Junk.stackLocation() + red("unknown command: ")+s);
					return;
				}
				current.add(s);
			}
		}
		
		if(map.isEmpty()) {
			System.out.println(Junk.stackLocation() + red("  no command specified"));
			printHelp();
		}
		
		List<CmdInit> found = new ArrayList<>();
		
		for (Entry<String, List<String>> e : map.entrySet()) {
			CmdInit c = command_map.get(e.getKey()).newInstance(); 
			if(!c.init(e.getValue()))
				return;
			found.add(c);
		}
		
		for (CmdInit c : found) {
			try {
				c.run();
			} finally {
				if(c instanceof AutoCloseable)
					((AutoCloseable) c).close();
				if(c instanceof Closeable)
					((Closeable) c).close();
			}
		}
			
	}

	private boolean helpOrVersion(String s) throws InstantiationException, IllegalAccessException {
		s = s.toLowerCase();
		if(s.equals("-h") || s.equals("--help")) {
			printHelp();
			return true;
		}
		if(s.equals("-v") || s.equals("--version")) {
			printVersion();
			return true;
		}
		return false;
	}

	private void printVersion() {
		LOGGER.info(yellow("Version: ")+System2.lookup("APP_VERSION"));
	}

	private void printHelp() throws InstantiationException, IllegalAccessException {
		LinkedHashSet<Class<? extends CmdInit>> set = new LinkedHashSet<>();
		command_map.forEach((s,t) -> set.add(t));
		for (Class<? extends CmdInit> c : set)
			c.newInstance().printHelp();
	}
}
