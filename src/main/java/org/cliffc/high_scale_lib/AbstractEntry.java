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
/*
 * Written by Cliff Click and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package org.cliffc.high_scale_lib;
import java.util.*;

/**
 * A simple implementation of {@link java.util.Map.Entry}.
 * Does not implement {@link java.util.Map.Entry.setValue}, that is done by users of the class.
 *
 * @since 1.5
 * @author Cliff Click
 * @param <TypeK> the type of keys maintained by this map
 * @param <TypeV> the type of mapped values
 */

abstract class AbstractEntry<TypeK,TypeV> implements Map.Entry<TypeK,TypeV> {
  /** Strongly typed key */
  protected final TypeK _key; 
  /** Strongly typed value */
  protected       TypeV _val;
  
  public AbstractEntry(final TypeK key, final TypeV val) { _key = key;        _val = val; }
  public AbstractEntry(final Map.Entry<TypeK,TypeV> e  ) { _key = e.getKey(); _val = e.getValue(); }
  /** Return "key=val" string */
  public String toString() { return _key + "=" + _val; }
  /** Return key */
  public TypeK getKey  () { return _key;  }
  /** Return val */
  public TypeV getValue() { return _val;  }

  /** Equal if the underlying key & value are equal */
  public boolean equals(final Object o) {
    if (!(o instanceof Map.Entry)) return false;
    final Map.Entry e = (Map.Entry)o;
    return eq(_key, e.getKey()) && eq(_val, e.getValue());
  }
  
  /** Compute <code>"key.hashCode() ^ val.hashCode()"</code> */
  public int hashCode() {
    return 
      ((_key == null) ? 0 : _key.hashCode()) ^
      ((_val == null) ? 0 : _val.hashCode());
  }
  
  private static boolean eq(final Object o1, final Object o2) {
    return (o1 == null ? o2 == null : o1.equals(o2));
  }
}

