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

	protected Integer generateId() {
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
		storage.remove(entity.getId());
	}

	public void deleteById(Integer id) {
		storage.remove(id);
	}

	public boolean existsById(Integer id) {
		return storage.containsKey(id);
	}

	public long count() {
		return storage.size();
	}

	public void deleteAll() {
		storage.clear();
		idGenerator.set(1);
	}

}
