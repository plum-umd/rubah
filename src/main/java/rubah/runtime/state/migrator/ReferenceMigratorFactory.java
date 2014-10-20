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
package rubah.runtime.state.migrator;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

import rubah.runtime.state.strategy.MigrationStrategy;

public class ReferenceMigratorFactory extends MigratorSubFactory {
	private final ReferenceMigrator theMigrator = new ReferenceMigrator();

	public ReferenceMigratorFactory(MigrationStrategy strategy) {
		super(strategy);
	}

	@Override
	public boolean canMigrate(Class<?> preClass) {
		return Reference.class.isAssignableFrom(preClass) || AtomicReference.class.isAssignableFrom(preClass);
	}

	@Override
	public Migrator buildMigrator() {
		return this.theMigrator;
	}

	private class ReferenceMigrator extends Migrator {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object doMigrate(Object obj) {

			Object ret = obj;
			synchronized (obj) {
				ret = strategy.getMapping().get(obj);

				if (ret != null) {
					return ret;
				} else if (obj.getClass().equals(SoftReference.class)) {
					SoftReference<?> ref = (SoftReference<?>) obj;
					ret = new SoftReference(strategy.migrate(ref.get()));
				} else if (obj.getClass().equals(WeakReference.class)) {
					WeakReference<?> ref = (WeakReference<?>) obj;

					ret = new WeakReference(strategy.migrate(ref.get()));
				} else if (obj.getClass().equals(AtomicReference.class)) {
					AtomicReference ref = (AtomicReference) obj;

					ret = new AtomicReference(strategy.migrate(ref.get()));
				}
				else
					// TODO find a more general way to migrated references
					// Taking a chance here...
					return ret;
			}

			if (ret == obj) {
				// Follow reference here
				this.follow(((Reference<?>) obj).get());
			}

			// Register in the mapping here
			strategy.getMapping().put(obj, ret);
			// Resulting reference should not be further migrated
			strategy.getMapping().put(ret, ret);

			return ret;
		}

		@Override
		public void followReferences(Object post) {
			// Already followed reference
		}

		@Override
		public Object registerMapping(Object pre, Object post) {
			// Already registered, do nothing
			return post;
		}
	}
}
