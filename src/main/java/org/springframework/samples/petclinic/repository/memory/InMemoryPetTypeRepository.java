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
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.owner.PetTypeRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory implementation of PetTypeRepository.
 */
@Repository
public class InMemoryPetTypeRepository extends InMemoryRepository<PetType, Integer> implements PetTypeRepository {

	@Override
	public List<PetType> findPetTypes() {
		return storage.values().stream().sorted(Comparator.comparing(PetType::getName)).collect(Collectors.toList());
	}

	@Override
	public List<PetType> findAll() {
		return storage.values().stream().sorted(Comparator.comparing(PetType::getName)).collect(Collectors.toList());
	}

	@Override
	public List<PetType> findAll(Sort sort) {
		return findAll(); // Simplified implementation
	}

	@Override
	public List<PetType> findAllById(Iterable<Integer> ids) {
		List<Integer> idList = new ArrayList<>();
		ids.forEach(idList::add);
		return storage.entrySet()
			.stream()
			.filter(entry -> idList.contains(entry.getKey()))
			.map(entry -> entry.getValue())
			.collect(Collectors.toList());
	}

	@Override
	public <S extends PetType> List<S> saveAll(Iterable<S> entities) {
		List<S> saved = new ArrayList<>();
		entities.forEach(entity -> saved.add((S) save(entity)));
		return saved;
	}

	@Override
	public void deleteAllById(Iterable<? extends Integer> ids) {
		ids.forEach(storage::remove);
	}

	@Override
	public void deleteAll(Iterable<? extends PetType> entities) {
		entities.forEach(entity -> storage.remove(entity.getId()));
	}

	@Override
	public PetType getReferenceById(Integer id) {
		return findById(id).orElseThrow(() -> new RuntimeException("Entity not found"));
	}

	@Override
	public PetType getById(Integer id) {
		return findById(id).orElse(null);
	}

	@Override
	public PetType getOne(Integer id) {
		return getById(id);
	}

	@Override
	public PetType save(PetType entity) {
		if (entity.isNew()) {
			entity.setId(generateId());
		}
		storage.put(entity.getId(), entity);
		return entity;
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
	public void deleteAllInBatch(Iterable<PetType> entities) {
		deleteAll(entities);
	}

	@Override
	public PetType saveAndFlush(PetType entity) {
		return save(entity);
	}

	@Override
	public org.springframework.data.domain.Page<PetType> findAll(org.springframework.data.domain.Pageable pageable) {
		List<PetType> all = findAll();
		int start = (int) pageable.getOffset();
		int end = Math.min(start + pageable.getPageSize(), all.size());

		if (start >= all.size()) {
			return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, all.size());
		}

		List<PetType> pageContent = all.subList(start, end);
		return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, all.size());
	}

	@Override
	public <S extends PetType> List<S> saveAllAndFlush(Iterable<S> entities) {
		return saveAll(entities);
	}

	@Override
	public void flush() {
		// No-op for in-memory storage
	}

	@Override
	public <S extends PetType> List<S> findAll(Example<S> example, Sort sort) {
		// Simplified implementation - not used in the application
		return new ArrayList<>();
	}

	@Override
	public <S extends PetType> List<S> findAll(Example<S> example) {
		// Simplified implementation - not used in the application
		return new ArrayList<>();
	}

	@Override
	public <S extends PetType> Optional<S> findOne(Example<S> example) {
		// Simplified implementation - not used in the application
		return Optional.empty();
	}

	@Override
	public <S extends PetType> Page<S> findAll(Example<S> example, Pageable pageable) {
		// Simplified implementation - not used in the application
		return new PageImpl<>(new ArrayList<>(), pageable, 0);
	}

	@Override
	public <S extends PetType> long count(Example<S> example) {
		// Simplified implementation - not used in the application
		return 0;
	}

	@Override
	public <S extends PetType> boolean exists(Example<S> example) {
		// Simplified implementation - not used in the application
		return false;
	}

	@Override
	public <S extends PetType, R> R findBy(Example<S> example,
			java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
		// Simplified implementation - not used in the application
		return null;
	}

	// Initialize with some default pet types
	{
		PetType dog = new PetType();
		dog.setId(1);
		dog.setName("dog");
		storage.put(1, dog);

		PetType cat = new PetType();
		cat.setId(2);
		cat.setName("cat");
		storage.put(2, cat);

		PetType bird = new PetType();
		bird.setId(3);
		bird.setName("bird");
		storage.put(3, bird);

		PetType fish = new PetType();
		fish.setId(4);
		fish.setName("fish");
		storage.put(4, fish);

		PetType lizard = new PetType();
		lizard.setId(5);
		lizard.setName("lizard");
		storage.put(5, lizard);

		PetType snake = new PetType();
		snake.setId(6);
		snake.setName("snake");
		storage.put(6, snake);

		PetType hamster = new PetType();
		hamster.setId(7);
		hamster.setName("hamster");
		storage.put(7, hamster);

		idGenerator.set(8);
	}

}
