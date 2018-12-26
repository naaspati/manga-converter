package samrock.converters.app.main;

import static sam.console.ANSI.red;

import java.io.File;
import java.util.List;
@Cmd({"-s", "--split"})
class Split extends CmdInitBase  {
  //   @Parameter
    List<String> args;
    
    // @Parameter(names= "--batch" , description = "Batch Double Page Splitter")
    public File batchDoublePageSplitter;

	@Override
	public boolean init(List<String> args) {
		//TODO
		throw new IllegalAccessError("not yet implmented");
	}

	@Override
	public void printHelp() {
		// TODO Auto-generated method stub
		System.out.println(red("not yet implmented"));
		
	}

	@Override
	public void run() throws Exception {
		// TODO Auto-generated method stub
		
	}
}