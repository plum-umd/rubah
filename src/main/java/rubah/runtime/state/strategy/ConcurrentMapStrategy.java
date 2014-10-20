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
package rubah.runtime.state.strategy;

import java.util.Set;

import rubah.runtime.state.ConcurrentHashMap;

public class ConcurrentMapStrategy implements MappingStrategy {
	private ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<Object, Object>();

//	private java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> instances = new java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>();

	@Override
	public Object get(Object pre) {
		return this.map.get(pre);
	}

	@Override
	public Object put(Object pre, Object post) {
		Object ret = this.map.putIfAbsent(pre, post);

		return (ret == null ? post : ret);
//		AtomicInteger i = instances.get(pre.getClass().getName());
//		if (i == null) {
//			i = new AtomicInteger(0);
//			instances.put(pre.getClass().getName(), i);
//		}
//
//		i.incrementAndGet();
	}

	@Override
	public int countMapped() {
		return this.map.size();
	}


//	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
//		in.defaultReadObject();
//		Thread t = new Thread(){
//			@Override
//			public void run() {
//				while (true) {
//					try {
//						Thread.sleep(1000);
//					} catch (InterruptedException e) {
//					}
//					LinkedList<Entry<String, AtomicInteger>> entries = new LinkedList<Entry<String,AtomicInteger>>(instances.entrySet());
//					Collections.sort(entries, new Comparator<Entry<String, AtomicInteger>>(){
//						public int compare(Entry<String, AtomicInteger> o1,
//								Entry<String, AtomicInteger> o2) {
//							return Integer.compare(o1.getValue().get(), o2.getValue().get());
//						}
//					});
//					for (Entry<String, AtomicInteger> entry : entries) {
//						System.out.println(entry.getValue().get() + "\t" + entry.getKey());
//					}
//					System.out.println("\n\n\n");
//				}
//			}
//		};
//		t.setDaemon(true);
//		t.start();
//	}

	@Override
	public void setUpdatedClassNames(Set<String> updatedClasses) {
		// Empty
	}
}
