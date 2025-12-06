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

import org.springframework.samples.petclinic.model.BaseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for in-memory repository implementations.
 *
 * @param <T> the entity type
 * @param <ID> the entity ID type
 */
public abstract class InMemoryRepository<T extends BaseEntity, ID> {

	protected final ConcurrentHashMap<Integer, T> storage = new ConcurrentHashMap<>();

	protected final AtomicInteger idGenerator = new AtomicInteger(1);

	// Queue to reuse deleted IDs for memory efficiency in mixed workloads
	protected final ConcurrentLinkedQueue<Integer> reusableIds = new ConcurrentLinkedQueue<>();

	protected Integer generateId() {
		// First try to reuse a deleted ID
		Integer reusedId = reusableIds.poll();
		if (reusedId != null) {
			return reusedId;
		}
		// If no reusable IDs, generate a new one
		return idGenerator.getAndIncrement();
	}

	public Optional<T> findById(Integer id) {
		return Optional.ofNullable(storage.get(id));
	}

	public List<T> findAll() {
		return new ArrayList<>(storage.values());
	}

	public T save(T entity) {
		if (entity.isNew()) {
			entity.setId(generateId());
		}
		storage.put(entity.getId(), entity);
		return entity;
	}

	public void delete(T entity) {
		if (storage.remove(entity.getId()) != null) {
			// Add the deleted ID to the reusable queue for ID reuse
			reusableIds.offer(entity.getId());
		}
	}

	public void deleteById(Integer id) {
		if (storage.remove(id) != null) {
			// Add the deleted ID to the reusable queue for ID reuse
			reusableIds.offer(id);
		}
	}

	public boolean existsById(Integer id) {
		return storage.containsKey(id);
	}

	public long count() {
		return storage.size();
	}

	public void deleteAll() {
		storage.clear();
		reusableIds.clear();
		idGenerator.set(1);
	}

}
