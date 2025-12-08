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

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Sharded in-memory implementation of OwnerRepository. Stores owners in memory shards
 * where each shard contains multiple owners in a contiguous memory block, simulating the
 * requested allocation pattern.
 */
@Repository
public class ShardedOwnerRepository extends InMemoryRepository<Owner, Integer> implements OwnerRepository {

	private static final int SHARD_SIZE = 1000; // 1000 owners per shard

	// Map of shardId -> OwnerShard
	private final ConcurrentMap<Integer, OwnerShard> shards = new ConcurrentHashMap<>();

	/**
	 * Calculate which shard an owner belongs to based on their ID.
	 */
	private int getShardId(int ownerId) {
		return ownerId / SHARD_SIZE;
	}

	/**
	 * Calculate the index within a shard for an owner ID.
	 */
	private int getIndexInShard(int ownerId) {
		return ownerId % SHARD_SIZE;
	}

	/**
	 * Get or create a shard for the given shard ID.
	 */
	private OwnerShard getOrCreateShard(int shardId) {
		return shards.computeIfAbsent(shardId, OwnerShard::new);
	}

	@Override
	public Page<Owner> findByLastNameStartingWith(String lastName, Pageable pageable) {
		// Collect all owners from all shards that match the criteria
		List<Owner> filtered = new ArrayList<>();
		for (OwnerShard shard : shards.values()) {
			for (Owner owner : shard.getAllOwners()) {
				if (owner.getLastName().startsWith(lastName)) {
					filtered.add(owner);
				}
			}
		}

		// Sort by last name
		filtered.sort(Comparator.comparing(Owner::getLastName));

		// Apply pagination
		int start = (int) pageable.getOffset();
		int end = Math.min(start + pageable.getPageSize(), filtered.size());

		if (start >= filtered.size()) {
			return new PageImpl<>(List.of(), pageable, filtered.size());
		}

		return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
	}

	@Override
	public List<Owner> findAll() {
		List<Owner> allOwners = new ArrayList<>();
		for (OwnerShard shard : shards.values()) {
			allOwners.addAll(shard.getAllOwners());
		}
		return allOwners.stream().sorted(Comparator.comparing(Owner::getLastName)).collect(Collectors.toList());
	}

	@Override
	public List<Owner> findAll(Sort sort) {
		return findAll(); // Simplified implementation
	}

	@Override
	public List<Owner> findAllById(Iterable<Integer> ids) {
		List<Owner> result = new ArrayList<>();
		for (Integer id : ids) {
			Owner owner = findById(id).orElse(null);
			if (owner != null) {
				result.add(owner);
			}
		}
		return result;
	}

	@Override
	public <S extends Owner> List<S> saveAll(Iterable<S> entities) {
		List<S> saved = new ArrayList<>();
		for (S entity : entities) {
			saved.add((S) save(entity));
		}
		return saved;
	}

	@Override
	public void deleteAllById(Iterable<? extends Integer> ids) {
		for (Integer id : ids) {
			deleteById(id);
		}
	}

	@Override
	public void deleteAll(Iterable<? extends Owner> entities) {
		for (Owner entity : entities) {
			delete(entity);
		}
	}

	@Override
	public Owner getReferenceById(Integer id) {
		return findById(id).orElseThrow(() -> new RuntimeException("Entity not found"));
	}

	@Override
	public Owner getById(Integer id) {
		return findById(id).orElse(null);
	}

	@Override
	public Owner getOne(Integer id) {
		return getById(id);
	}

	@Override
	public Optional<Owner> findById(Integer id) {
		if (id == null) {
			return Optional.empty();
		}

		int shardId = getShardId(id);
		OwnerShard shard = shards.get(shardId);
		if (shard == null) {
			return Optional.empty();
		}

		int index = getIndexInShard(id);
		Owner owner = shard.loadOwner(index);
		return Optional.ofNullable(owner);
	}

	@Override
	public Owner save(Owner entity) {
		if (entity.isNew()) {
			entity.setId(generateId());
		}

		int shardId = getShardId(entity.getId());
		int index = getIndexInShard(entity.getId());

		OwnerShard shard = getOrCreateShard(shardId);
		if (!shard.storeOwner(index, entity)) {
			throw new RuntimeException("Failed to store owner in shard");
		}

		return entity;
	}

	@Override
	public void delete(Owner entity) {
		deleteById(entity.getId());
	}

	@Override
	public void deleteById(Integer id) {
		if (id == null) {
			return;
		}

		int shardId = getShardId(id);
		OwnerShard shard = shards.get(shardId);
		if (shard != null) {
			int index = getIndexInShard(id);
			shard.deleteOwner(index);
		}
	}

	@Override
	public boolean existsById(Integer id) {
		return findById(id).isPresent();
	}

	@Override
	public long count() {
		long total = 0;
		for (OwnerShard shard : shards.values()) {
			total += shard.getOccupiedCount();
		}
		return total;
	}

	@Override
	public void deleteAll() {
		for (OwnerShard shard : shards.values()) {
			shard.clear();
		}
		shards.clear();
		super.deleteAll(); // Reset ID generator
	}

	@Override
	public void deleteAllInBatch() {
		deleteAll();
	}

	@Override
	public void deleteAllByIdInBatch(Iterable<Integer> ids) {
		deleteAllById(ids);
	}

	@Override
	public void deleteAllInBatch(Iterable<Owner> entities) {
		deleteAll(entities);
	}

	@Override
	public Owner saveAndFlush(Owner entity) {
		return save(entity);
	}

	@Override
	public Page<Owner> findAll(Pageable pageable) {
		List<Owner> all = findAll();
		int start = (int) pageable.getOffset();
		int end = Math.min(start + pageable.getPageSize(), all.size());

		if (start >= all.size()) {
			return new PageImpl<>(List.of(), pageable, all.size());
		}

		List<Owner> pageContent = all.subList(start, end);
		return new PageImpl<>(pageContent, pageable, all.size());
	}

	@Override
	public <S extends Owner> List<S> saveAllAndFlush(Iterable<S> entities) {
		return saveAll(entities);
	}

	@Override
	public void flush() {
		// No-op for in-memory storage
	}

	@Override
	public <S extends Owner> List<S> findAll(Example<S> example, Sort sort) {
		// Simplified implementation - not used in the application
		return new ArrayList<>();
	}

	@Override
	public <S extends Owner> List<S> findAll(Example<S> example) {
		// Simplified implementation - not used in the application
		return new ArrayList<>();
	}

	@Override
	public <S extends Owner> Optional<S> findOne(Example<S> example) {
		// Simplified implementation - not used in the application
		return Optional.empty();
	}

	@Override
	public <S extends Owner> Page<S> findAll(Example<S> example, Pageable pageable) {
		// Simplified implementation - not used in the application
		return new PageImpl<>(new ArrayList<>(), pageable, 0);
	}

	@Override
	public <S extends Owner> long count(Example<S> example) {
		// Simplified implementation - not used in the application
		return 0;
	}

	@Override
	public <S extends Owner> boolean exists(Example<S> example) {
		// Simplified implementation - not used in the application
		return false;
	}

	@Override
	public <S extends Owner, R> R findBy(Example<S> example,
			java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
		// Simplified implementation - not used in the application
		return null;
	}

	/**
	 * Get all owners in the shard that contains the specified owner ID. This simulates
	 * loading the entire 1MB memory block for that shard.
	 */
	public List<Owner> findAllOwnersInShard(int ownerId) {
		int shardId = getShardId(ownerId);
		OwnerShard shard = shards.get(shardId);
		if (shard == null) {
			return new ArrayList<>();
		}
		return shard.getAllOwners();
	}

}
