/*******************************************************************************
 *  	Copyright 2014,
 *  		Luis Pina <luis@luispina.me>,
 *  		Michael Hicks <mwh@cs.umd.edu>
 *  	
 *  	This file is part of Rubah.
 *
 *     Rubah is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Rubah is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Rubah.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package rubah.tools;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.FileConverter;

public class RubahTool {
	private static final Map<String, Class<? extends ReadTool>> tools;

	static {
		tools = new HashMap<String, Class<? extends ReadTool>>();
		tools.put(BootstrapJarProcessor.TOOL_NAME, BootstrapJarProcessor.class);
		tools.put(AnalysisPrinter.TOOL_NAME, AnalysisPrinter.class);
		tools.put(MethodTracer.TOOL_NAME, MethodTracer.class);
		tools.put(RubahPostProcessor.TOOL_NAME, RubahPostProcessor.class);
	}
	
	private static class BootstrapParameters {
		@Parameter(
				description="Tool name",
				names={"-t","--tool"},
				required=true)
		private String tool;
	}
	
	public static class Parameters {
		@Parameter(
				description="Tool name",
				names={"-t","--tool"},
				required=true)
		private String tool;

		@Parameter(
				converter=FileConverter.class,
				description="Input jar file",
				names={"-i","--in-jar"},
				required=true)
		protected File injar;
	}
	
	private RubahTool() { /* Empty */ }

	private static ReadTool parseArgs(String[] args) throws InstantiationException, IllegalAccessException {
		
		String[] bootstrapArgs = new String[]{"", ""};
		
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("-t") || arg.equals("--tool")) {
				bootstrapArgs[0] = arg;
				bootstrapArgs[1] = args[++i];
				break;
			}
		}
		
		BootstrapParameters bootstrap = new BootstrapParameters();
		JCommander argParser = new JCommander(bootstrap);
		
		try {
			argParser.parse(bootstrapArgs);
		} catch (ParameterException e) {
			System.out.println(e.getMessage());
			argParser.usage();
			System.out.println("Available tools " + tools.keySet());
			System.exit(1);
		}

		if (!tools.containsKey(bootstrap.tool)) {
			argParser.usage();
			System.out.println("Unknown tool. Available tools "+tools.keySet());
			System.exit(1);
		}
		
		ReadTool ret = tools.get(bootstrap.tool).newInstance();
		argParser = new JCommander(ret.getParameters());
		
		try {
			argParser.parse(args);
		} catch (ParameterException e) {
			System.out.println(e.getMessage());
			argParser.usage();
			System.out.println("Available tools " + tools.keySet());
			System.exit(1);
		}

		return ret;
	}

	public static void main(String[] args) {
		try {
			parseArgs(args).processJar();

		} catch (IOException e) {
			throw new Error(e);
		} catch (InstantiationException e) {
			throw new Error(e);
		} catch (IllegalAccessException e) {
			throw new Error(e);
		}
	}

}