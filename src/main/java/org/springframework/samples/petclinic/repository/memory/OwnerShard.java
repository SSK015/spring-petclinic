/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.repository.memory;

import org.springframework.samples.petclinic.owner.Owner;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Memory shard for storing multiple Owner objects in a contiguous memory block. Each
 * shard holds up to 1000 owners to simulate ~1MB memory allocation pattern.
 */
public class OwnerShard {

	public static final int SHARD_SIZE = 50;

	private final int shardId;

	private final Owner[] owners;

	private final BitSet occupied;

	private final Object[] locks; // Fine-grained locking for each slot

	public OwnerShard(int shardId) {
		this.shardId = shardId;
		this.owners = new Owner[SHARD_SIZE];
		this.occupied = new BitSet(SHARD_SIZE);
		this.locks = new Object[SHARD_SIZE];
		for (int i = 0; i < SHARD_SIZE; i++) {
			locks[i] = new Object();
		}
	}

	/**
	 * Store an owner in this shard at the specified index. Returns true if successful,
	 * false if index out of bounds. Allows overwriting if the slot is occupied by the
	 * same owner (update operation).
	 */
	public boolean storeOwner(int index, Owner owner) {
		if (index < 0 || index >= SHARD_SIZE) {
			return false;
		}

		synchronized (locks[index]) {
			// If slot is occupied, check if it's the same owner (update) or different
			// (conflict)
			if (occupied.get(index)) {
				Owner existing = owners[index];
				if (existing != null && existing.getId() != null && owner.getId() != null
						&& existing.getId().equals(owner.getId())) {
					// Same owner, allow update
					owners[index] = owner;
					return true;
				}
				// Different owner in this slot, conflict
				return false;
			}
			// Slot is free, insert new owner
			owners[index] = owner;
			occupied.set(index);
			return true;
		}
	}

	/**
	 * Load an owner from this shard at the specified index. Returns null if not found or
	 * index out of bounds.
	 */
	public Owner loadOwner(int index) {
		if (index < 0 || index >= SHARD_SIZE) {
			return null;
		}

		synchronized (locks[index]) {
			return occupied.get(index) ? owners[index] : null;
		}
	}

	/**
	 * Delete an owner from this shard at the specified index. Returns true if
	 * successfully deleted, false if not found.
	 */
	public boolean deleteOwner(int index) {
		if (index < 0 || index >= SHARD_SIZE) {
			return false;
		}

		synchronized (locks[index]) {
			if (!occupied.get(index)) {
				return false;
			}
			owners[index] = null;
			occupied.clear(index);
			return true;
		}
	}

	/**
	 * Get all owners currently stored in this shard. This simulates loading the entire
	 * 1MB memory block.
	 */
	public List<Owner> getAllOwners() {
		List<Owner> result = new ArrayList<>();
		for (int i = 0; i < SHARD_SIZE; i++) {
			synchronized (locks[i]) {
				if (occupied.get(i)) {
					result.add(owners[i]);
				}
			}
		}
		return result;
	}

	/**
	 * Check if a slot is occupied.
	 */
	public boolean isOccupied(int index) {
		if (index < 0 || index >= SHARD_SIZE) {
			return false;
		}
		synchronized (locks[index]) {
			return occupied.get(index);
		}
	}

	/**
	 * Get the number of occupied slots in this shard.
	 */
	public int getOccupiedCount() {
		return occupied.cardinality();
	}

	/**
	 * Get the shard ID.
	 */
	public int getShardId() {
		return shardId;
	}

	/**
	 * Get the maximum capacity of this shard.
	 */
	public int getCapacity() {
		return SHARD_SIZE;
	}

	/**
	 * Clear all owners from this shard.
	 */
	public void clear() {
		for (int i = 0; i < SHARD_SIZE; i++) {
			synchronized (locks[i]) {
				owners[i] = null;
				occupied.clear(i);
			}
		}
	}

}
