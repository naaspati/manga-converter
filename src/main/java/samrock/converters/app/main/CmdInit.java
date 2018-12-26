package samrock.converters.app.main;

import java.util.List;

public interface CmdInit {
	public boolean init(List<String> args);
	public void printHelp();
	public void run() throws Exception;
}
