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
package rubah.tools.updater;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import rubah.runtime.state.strategy.ArrayStrategy;
import rubah.runtime.state.strategy.ConcurrentMapStrategy;
import rubah.runtime.state.strategy.EagerLazy;
import rubah.runtime.state.strategy.ForkJoinStrategy;
import rubah.runtime.state.strategy.ForwardFieldStrategy;
import rubah.runtime.state.strategy.FullyLazyMonolithic;
import rubah.runtime.state.strategy.IdentityMapStrategy;
import rubah.runtime.state.strategy.Lazy;
import rubah.runtime.state.strategy.MappingStrategy;
import rubah.runtime.state.strategy.MigrationStrategy;
import rubah.runtime.state.strategy.SingleThreaded;
import rubah.runtime.state.strategy.ThreadPoolStrategy;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.FileConverter;


public class ParsingArguments extends Filter {

	private static abstract class CommonParameters {
		@Parameter(
				description="Port where client is listening",
				names={"-p","--port"},
				required=true)
		protected int port;

		public enum MigrationStrategyArgument {
			SINGLE_IDENTITY,
			SINGLE_CONCURRENT,
			THREAD_POOL,
			FORK_JOIN,
			FULL_LAZY,
			LAZY,
			EAGER_LAZY
		}

		public enum MappingStrategyArgument {
			IDENTITY_MAP,
			CONCURRENT_MAP,
			FORWARD_FIELD,
			ARRAY,
		}

		@Parameter(
				description="Strategy to traverse/migrate the program state: SINGLE_IDENTITY, SINGLE_CONCURRENT, THREAD_POOL, FORK_JOIN, LAZY, FULL_LAZY, or EAGER_LAZY",
				names={"-s","--strategy"},
				required=false)
		protected MigrationStrategyArgument migrationStrategy = MigrationStrategyArgument.SINGLE_IDENTITY;

		@Parameter(
				description="Strategy to map the old objects to new ones: IDENTITY_MAP, CONCURRENT_MAP, FORWARD_FIELD, or ARRAY",
				names={"-m","--map-strategy"},
				variableArity=true,
				required=false)
		protected List<MappingStrategyArgument> mappingStrategies =
					new LinkedList<MappingStrategyArgument>(Arrays.asList(new MappingStrategyArgument[]{MappingStrategyArgument.CONCURRENT_MAP}));

		@Parameter(
				description="Number of threads to use with applicable strategy, defaults to Runtime.availableProcessors",
				names={"-t","--threads"},
				required=false)
		protected int nThreads = Runtime.getRuntime().availableProcessors();

		@Parameter(
				converter=FileConverter.class,
				description="Compiled update class file",
				names={"-c","--update-class-file"},
				required=false)
		protected File updateClass;

		@Parameter(
				description="Name of update class present in new version jar",
				names={"-n","--update-class-name"},
				required=false)
		protected String updateClassName;

		@Parameter(
				converter=FileConverter.class,
				description="Update package with the needed bytecode",
				names={"-u","--update-package"},
				required=false)
		protected File updatePackage;

		public void parseLine(String[] line) {
			JCommander argParser = new JCommander(this);
			argParser.parse(line);
			this.validateArgs();
		}

		protected void validateArgs() {
			// Empty by default
		}

		public void printUsage() throws ParameterException {
			new JCommander(this).usage();
		}

		public final void setState(UpdateState state) {
			state.setPort(this.port);
			state.setUpdatePackage(this.updatePackage);

			MigrationStrategy migrationStrategy;
			MappingStrategy mappingStrategy = null;

			Collections.reverse(this.mappingStrategies);
			for (MappingStrategyArgument arg : this.mappingStrategies) {
				switch (arg) {
					default:
					case IDENTITY_MAP:
						mappingStrategy = new IdentityMapStrategy();
						break;
					case CONCURRENT_MAP:
						mappingStrategy = new ConcurrentMapStrategy();
						break;
					case FORWARD_FIELD:
						mappingStrategy = new ForwardFieldStrategy(mappingStrategy);
						break;
					case ARRAY:
						mappingStrategy = new ArrayStrategy(mappingStrategy);
						break;
				}
			}

			switch (this.migrationStrategy) {
				default:
				case SINGLE_IDENTITY:
					migrationStrategy = new SingleThreaded(mappingStrategy);
					break;
				case SINGLE_CONCURRENT:
					migrationStrategy = new SingleThreaded(mappingStrategy);
					break;
				case THREAD_POOL:
					migrationStrategy = new ThreadPoolStrategy(mappingStrategy, this.nThreads);
					break;
				case FORK_JOIN:
					migrationStrategy = new ForkJoinStrategy(mappingStrategy, this.nThreads);
					break;
				case LAZY:
					migrationStrategy = new Lazy(mappingStrategy);
					state.setLazy(true);
					break;
				case FULL_LAZY:
					migrationStrategy = new FullyLazyMonolithic();
					break;
				case EAGER_LAZY:
					migrationStrategy = new EagerLazy(mappingStrategy, this.nThreads);
					break;
			}

			state.setMigrationStrategy(migrationStrategy);

			if (this.updateClass != null) {
				state.setUpdateClassFile(this.updateClass);
			}
			if (this.updateClassName != null) {
				state.setUpdateClassName(this.updateClassName);
			}

			this.doSetState(state);
		}

		protected abstract void doSetState(UpdateState state);
	}

	private static class V0V0ModeParameters extends CommonParameters {
		@Parameter(
				description="Do a v0v0 update and copy all program state",
				names={"-v0v0"},
				required=true)
		protected boolean v0v0;

		@Parameter(
				description="Perform stop-and-go",
				names={"-stopAndGo"},
				required=false)
		protected boolean stopAndGo= false;

		@Override
		public void doSetState(UpdateState state) {
			state.setV0V0(true);
			state.setStopAndGo(stopAndGo);
		}
	}

	private static class V0V1ModeParameters extends CommonParameters {
		@Parameter(
				description="Do a real update",
				names={"-v0v1"},
				required=false)
		protected boolean v0v1;

		@Parameter(
				converter=FileConverter.class,
				description="Update descriptor",
				names={"-d","--descriptor"},
				required=true)
		protected File descriptor;

		@Parameter(
				converter=FileConverter.class,
				description="Jar file with new version",
				names={"-j1"},
				required=true)
		protected File jarFile1;

		@Override
		protected void validateArgs() {
			if (!(this.updateClass == null ^ this.updateClassName == null)) {
				throw new ParameterException("Please provide an update class file or an update class name (any one and not both)");
			}
		}

		@Override
		public void doSetState(UpdateState state) {
			state.setV0V0(false);
			state.setDescriptor(this.descriptor);
			state.setNewJar(this.jarFile1);
		}
	}

	private static CommonParameters[] getParameters() {
		return new CommonParameters[]{
				new V0V0ModeParameters(),
				new V0V1ModeParameters()
		};
	}

	@Override
	public void execute(UpdateState state) {

		CommonParameters validParameters = null;

		for (CommonParameters parameters : getParameters()) {
			try {
				parameters.parseLine(state.getCommandLine());
				validParameters = parameters;
				break;
			} catch (ParameterException e) {
				continue;
			}
		}

		if (validParameters == null) {
			System.out.println("Error parsing arguments: possible inputs:");
			for (CommonParameters parameters : getParameters()) {
				try {
					parameters.parseLine(state.getCommandLine());
				} catch (ParameterException e) {
					System.out.println(e.getMessage());
				}
				parameters.printUsage();
			}
			System.exit(1);
		}

		validParameters.setState(state);
	}
}
