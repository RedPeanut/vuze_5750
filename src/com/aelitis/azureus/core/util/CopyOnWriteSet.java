/*
 * Created on May 30, 2008
 * Created by Paul Gardner
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */


package com.aelitis.azureus.core.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class
CopyOnWriteSet<T>
{
	private final boolean	is_identify;

	private volatile Set<T>	set;

	private boolean	visible = false;

	public
	CopyOnWriteSet(
		boolean	identity_hash_set) {
		is_identify = identity_hash_set;

		if (is_identify) {

			set = new IdentityHashSet<T>();

		} else {

			set = new HashSet<T>();
		}
	}

	public boolean add(
		T		o) {
		boolean	result;

		synchronized(this) {

			if (visible) {

				Set<T> new_set;

				if (is_identify) {

					new_set = new IdentityHashSet<T>(set);

				} else {

					new_set = new HashSet<T>(set);
				}

				result = new_set.add(o);

				set = new_set;

				visible = false;

			} else {

				result = set.add(o);
			}
		}

		return (result);
	}

	public boolean remove(
		T		o) {
		synchronized(this) {

			if (visible) {

				Set<T> new_set;

				if (is_identify) {

					new_set = new IdentityHashSet<T>(set);

				} else {

					new_set = new HashSet<T>(set);
				}

				boolean res = new_set.remove(o);

				set = new_set;

				visible = false;

				return (res);

			} else {

				return (set.remove( o));
			}
		}
	}

	public boolean contains(
		T	o) {
		return (set.contains( o));
	}

	public void clear() {
		synchronized(this) {

			if (visible) {

				Set<T> new_set;

				if (is_identify) {

					new_set = new IdentityHashSet<T>(set);

				} else {

					new_set = new HashSet<T>(set);
				}

				set = new_set;

				visible = false;

			} else {

				set.clear();
			}
		}
	}

	public int size() {
		return ( set.size());
	}

	public Set<T>
	getSet() {
		synchronized(this) {

			visible = true;

			return (set);
		}
	}

	public Iterator<T>
	iterator() {
		synchronized(this) {

			visible = true;

			return (new CopyOnWriteSetIterator( set.iterator()));
		}
	}

	private class
	CopyOnWriteSetIterator
		implements Iterator<T>
	{
		private final Iterator<T>	it;
		private T			last;

		protected
		CopyOnWriteSetIterator(
			Iterator<T>		_it) {
			it		= _it;
		}

		public boolean
		hasNext() {
			return ( it.hasNext());
		}

		public T
		next() {
			last	= it.next();

			return (last);
		}

		public void
		remove() {
				// don't actually remove it from the iterator. can't go backwards with this iterator so this is
				// not a problem

			if (last == null) {

				throw (new IllegalStateException("next has not been called!"));
			}

			CopyOnWriteSet.this.remove(last);
		}
	}
}
